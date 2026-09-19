package com.febrie.demo_bk.article.application.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.febrie.demo_bk.article.application.dto.ArticleListDTO;
import com.febrie.demo_bk.article.infrastructure.cache.ArticleListCacheStore;
import com.febrie.demo_bk.article.infrastructure.cache.ArticleViewStore;
import com.febrie.demo_bk.article.infrastructure.cache.CacheValue;
import com.febrie.demo_bk.article.infrastructure.cache.CacheValue.CacheState;
import com.febrie.demo_bk.article.infrastructure.persistence.ArticleMapper;
import com.febrie.demo_bk.article.infrastructure.persistence.BlogArticle;
import com.febrie.demo_bk.shared.error.ResourceNotFoundException;
import com.febrie.demo_bk.shared.infrastructure.redis.RedisDistributedLockService;
import com.febrie.demo_bk.shared.infrastructure.redis.RedisDistributedLockService.LockHandle;
import com.febrie.demo_bk.shared.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 将文章 ID 批量水合为列表 DTO，并统一处理列表缓存、回源锁和实时浏览量合并。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleListHydrator {

    private static final int MIN_CACHE_POLL_MILLIS = 30;
    private static final int MAX_CACHE_POLL_MILLIS = 80;
    private static final Duration CACHE_LOCK_LEASE = Duration.ofSeconds(10L);
    private static final Duration CACHE_WAIT = Duration.ofMillis(800L);

    private final ArticleMapper articleMapper;
    private final ArticleListCacheStore listCacheStore;
    private final ArticleViewStore articleViewStore;
    private final RedisDistributedLockService lockService;

    /**
     * Redis 状态无法确认时，直接按最新顺序查询完整 DTO 页面。
     */
    public PageResult<ArticleListDTO> loadLatestMysqlPage(int page, int size) {
        return loadMysqlPage(page, size, false, true);
    }

    /**
     * 排行榜不可用时，按 MySQL 浏览量快照查询完整 DTO 页面。
     */
    public PageResult<ArticleListDTO> loadViewRankMysqlPage(int page, int size) {
        return loadMysqlPage(page, size, true, false);
    }

    /**
     * 批量加载 DTO，并区分可修复的确认不存在、仅本请求不存在和未知状态。
     */
    HydratedArticleList hydrate(List<Integer> articleIds, Map<Integer, Long> rankScores) {
        if (articleIds == null || articleIds.isEmpty()) {
            return HydratedArticleList.empty();
        }

        Set<Integer> uniqueIds = new LinkedHashSet<>(articleIds);
        Map<Integer, ArticleListDTO> dtoById = new HashMap<>();
        Set<Integer> confirmedMissingIds = new LinkedHashSet<>();
        Set<Integer> requestOnlyMissingIds = new LinkedHashSet<>();
        Set<Integer> unresolvedIds = new LinkedHashSet<>();
        Set<Integer> trueMissIds = new LinkedHashSet<>();
        Set<Integer> fallbackIds = new LinkedHashSet<>();

        try {
            Map<Integer, CacheValue<ArticleListDTO>> states =
                    listCacheStore.getStates(new ArrayList<>(uniqueIds));
            collectCacheStates(
                    uniqueIds,
                    states,
                    dtoById,
                    confirmedMissingIds,
                    trueMissIds
            );
        } catch (RuntimeException exception) {
            log.warn("批量读取文章DTO缓存失败，改为无锁批量回表", exception);
            fallbackIds.addAll(uniqueIds);
        }

        if (!trueMissIds.isEmpty()) {
            hydrateOwnedAndContendedIds(
                    trueMissIds,
                    dtoById,
                    confirmedMissingIds,
                    requestOnlyMissingIds,
                    unresolvedIds,
                    fallbackIds
            );
        }

        if (!fallbackIds.isEmpty()) {
            hydrateFallbackIds(
                    fallbackIds,
                    dtoById,
                    requestOnlyMissingIds,
                    unresolvedIds
            );
        }

        Map<Integer, Long> realtimeScores = rankScores == null
                ? loadRealtimeScores(articleIds)
                : rankScores;
        List<ArticleListDTO> result = new ArrayList<>(articleIds.size());
        for (Integer articleId : articleIds) {
            ArticleListDTO dto = dtoById.get(articleId);
            if (dto == null) {
                continue;
            }

            ArticleListDTO responseDto = copyArticleListDto(dto);
            Long realtimeScore = realtimeScores.get(articleId);
            if (realtimeScore != null) {
                responseDto.setViewCount(realtimeScore);
            }
            result.add(responseDto);
        }

        return new HydratedArticleList(
                result,
                Collections.unmodifiableSet(confirmedMissingIds),
                Collections.unmodifiableSet(requestOnlyMissingIds),
                Collections.unmodifiableSet(unresolvedIds)
        );
    }

    private PageResult<ArticleListDTO> loadMysqlPage(int page,
                                                      int size,
                                                      boolean viewCountSort,
                                                      boolean applyRealtimeScores) {
        LambdaQueryWrapper<BlogArticle> queryWrapper = new LambdaQueryWrapper<>();
        if (viewCountSort) {
            queryWrapper
                    .orderByDesc(BlogArticle::getViewCount)
                    .orderByDesc(BlogArticle::getId);
        } else {
            queryWrapper
                    .orderByDesc(BlogArticle::getArticleDate)
                    .orderByDesc(BlogArticle::getId);
        }

        Page<ArticleListDTO> result = articleMapper.selectArticleListPage(
                new Page<>(page, size),
                queryWrapper
        );
        normalizeArticleListDtos(result.getRecords());
        validatePageBounds(result.getTotal(), page, size);

        if (applyRealtimeScores) {
            applyRealtimeScores(result.getRecords());
        }
        return new PageResult<>(result.getRecords(), result.getTotal(), page);
    }

    private void hydrateOwnedAndContendedIds(
            Set<Integer> trueMissIds,
            Map<Integer, ArticleListDTO> dtoById,
            Set<Integer> confirmedMissingIds,
            Set<Integer> requestOnlyMissingIds,
            Set<Integer> unresolvedIds,
            Set<Integer> fallbackIds) {
        Map<Integer, LockHandle> ownedLocks = new LinkedHashMap<>();
        Set<Integer> contendedIds = new LinkedHashSet<>();

        for (Integer articleId : trueMissIds) {
            try {
                LockHandle lockHandle = lockService.tryLock(
                        listCacheStore.lockKey(articleId),
                        CACHE_LOCK_LEASE
                );
                if (lockHandle == null) {
                    contendedIds.add(articleId);
                } else {
                    ownedLocks.put(articleId, lockHandle);
                }
            } catch (RuntimeException exception) {
                log.warn("获取文章DTO回源锁失败，articleId={}", articleId, exception);
                fallbackIds.add(articleId);
            }
        }

        if (!ownedLocks.isEmpty()) {
            try {
                List<ArticleListDTO> databaseDtos = articleMapper.selectArticleListByIds(
                        ownedLocks.keySet()
                );
                normalizeArticleListDtos(databaseDtos);
                Map<Integer, ArticleListDTO> databaseDtoById = mapDtosById(databaseDtos);

                for (Map.Entry<Integer, LockHandle> entry : ownedLocks.entrySet()) {
                    Integer articleId = entry.getKey();
                    LockHandle lockHandle = entry.getValue();
                    ArticleListDTO dto = databaseDtoById.get(articleId);
                    if (dto != null) {
                        dtoById.put(articleId, dto);
                        try {
                            if (!listCacheStore.publish(articleId, dto, lockHandle)) {
                                log.warn("文章DTO正常缓存发布被拒绝，articleId={}", articleId);
                            }
                        } catch (RuntimeException exception) {
                            // 已查询到的 DTO 仍可用于当前响应，缓存失败只记录日志。
                            log.warn("发布文章DTO正常缓存失败，articleId={}", articleId, exception);
                        }
                    } else {
                        publishMissingState(
                                articleId,
                                lockHandle,
                                confirmedMissingIds,
                                requestOnlyMissingIds
                        );
                    }
                }
            } catch (RuntimeException exception) {
                unresolvedIds.addAll(ownedLocks.keySet());
                log.warn("持锁批量查询文章DTO失败，ids={}", ownedLocks.keySet(), exception);
            } finally {
                ownedLocks.forEach((articleId, lockHandle) ->
                        unlockSafely(lockHandle, "文章DTO回源锁:" + articleId));
            }
        }

        if (!contendedIds.isEmpty()) {
            waitForContendedIds(
                    contendedIds,
                    dtoById,
                    confirmedMissingIds,
                    fallbackIds
            );
        }
    }

    private void waitForContendedIds(Set<Integer> contendedIds,
                                     Map<Integer, ArticleListDTO> dtoById,
                                     Set<Integer> confirmedMissingIds,
                                     Set<Integer> fallbackIds) {
        Set<Integer> pendingIds = new LinkedHashSet<>(contendedIds);
        long deadlineNanos = System.nanoTime() + CACHE_WAIT.toNanos();

        while (!pendingIds.isEmpty() && System.nanoTime() < deadlineNanos) {
            try {
                Map<Integer, CacheValue<ArticleListDTO>> states =
                        listCacheStore.getStates(new ArrayList<>(pendingIds));
                for (Integer articleId : new ArrayList<>(pendingIds)) {
                    CacheValue<ArticleListDTO> state = states.get(articleId);
                    if (state == null || state.state() == CacheState.MISS) {
                        continue;
                    }
                    pendingIds.remove(articleId);
                    if (state.state() == CacheState.HIT) {
                        dtoById.put(articleId, state.value());
                    } else {
                        confirmedMissingIds.add(articleId);
                    }
                }
            } catch (RuntimeException exception) {
                log.warn("轮询竞争中的文章DTO缓存失败，改为无锁批量回表", exception);
                break;
            }

            if (!pendingIds.isEmpty()) {
                sleepBeforeCachePoll(deadlineNanos);
            }
        }
        fallbackIds.addAll(pendingIds);
    }

    private void hydrateFallbackIds(Set<Integer> fallbackIds,
                                    Map<Integer, ArticleListDTO> dtoById,
                                    Set<Integer> requestOnlyMissingIds,
                                    Set<Integer> unresolvedIds) {
        try {
            List<ArticleListDTO> databaseDtos = articleMapper.selectArticleListByIds(fallbackIds);
            normalizeArticleListDtos(databaseDtos);
            Map<Integer, ArticleListDTO> databaseDtoById = mapDtosById(databaseDtos);
            dtoById.putAll(databaseDtoById);
            for (Integer articleId : fallbackIds) {
                if (!databaseDtoById.containsKey(articleId)) {
                    // 无锁回表只能影响当前响应，不能据此修复共享索引。
                    requestOnlyMissingIds.add(articleId);
                }
            }
        } catch (RuntimeException exception) {
            unresolvedIds.addAll(fallbackIds);
            log.warn("无锁批量查询文章DTO失败，ids={}", fallbackIds, exception);
        }
    }

    private void collectCacheStates(
            Set<Integer> articleIds,
            Map<Integer, CacheValue<ArticleListDTO>> states,
            Map<Integer, ArticleListDTO> dtoById,
            Set<Integer> confirmedMissingIds,
            Set<Integer> trueMissIds) {
        for (Integer articleId : articleIds) {
            CacheValue<ArticleListDTO> state = states.get(articleId);
            if (state == null || state.state() == CacheState.MISS) {
                trueMissIds.add(articleId);
            } else if (state.state() == CacheState.NEGATIVE) {
                confirmedMissingIds.add(articleId);
            } else {
                dtoById.put(articleId, state.value());
            }
        }
    }

    private void publishMissingState(
            int articleId,
            LockHandle lockHandle,
            Set<Integer> confirmedMissingIds,
            Set<Integer> requestOnlyMissingIds) {
        try {
            if (listCacheStore.publishMissing(articleId, lockHandle)) {
                confirmedMissingIds.add(articleId);
            } else {
                requestOnlyMissingIds.add(articleId);
                log.warn("文章DTO负缓存发布被拒绝，articleId={}", articleId);
            }
        } catch (RuntimeException exception) {
            requestOnlyMissingIds.add(articleId);
            log.warn("发布文章DTO负缓存失败，articleId={}", articleId, exception);
        }
    }

    private Map<Integer, ArticleListDTO> mapDtosById(
            Collection<ArticleListDTO> articleListDtos) {
        Map<Integer, ArticleListDTO> result = new HashMap<>();
        if (articleListDtos == null) {
            return result;
        }
        for (ArticleListDTO dto : articleListDtos) {
            if (dto != null && dto.getId() != null) {
                result.put(dto.getId(), dto);
            }
        }
        return result;
    }

    private Map<Integer, Long> loadRealtimeScores(List<Integer> articleIds) {
        try {
            List<Long> scores = articleViewStore.getViewScores(articleIds);
            Map<Integer, Long> result = new HashMap<>();
            int resultSize = Math.min(articleIds.size(), scores.size());
            for (int index = 0; index < resultSize; index++) {
                result.put(articleIds.get(index), scores.get(index));
            }
            return result;
        } catch (RuntimeException exception) {
            // 排行榜故障时继续返回 DTO 内的 MySQL 浏览量快照。
            log.warn("读取文章实时浏览量失败，使用MySQL快照", exception);
            return Collections.emptyMap();
        }
    }

    private void applyRealtimeScores(List<ArticleListDTO> articleListDtos) {
        if (articleListDtos == null || articleListDtos.isEmpty()) {
            return;
        }
        List<Integer> articleIds = articleListDtos.stream()
                .map(ArticleListDTO::getId)
                .toList();
        Map<Integer, Long> scores = loadRealtimeScores(articleIds);
        articleListDtos.forEach(dto -> {
            Long score = scores.get(dto.getId());
            if (score != null) {
                dto.setViewCount(score);
            }
        });
    }

    private void sleepBeforeCachePoll(long deadlineNanos) {
        long remainingMillis = Math.max(
                1L,
                Duration.ofNanos(deadlineNanos - System.nanoTime()).toMillis()
        );
        long sleepMillis = Math.min(
                remainingMillis,
                ThreadLocalRandom.current().nextInt(
                        MIN_CACHE_POLL_MILLIS,
                        MAX_CACHE_POLL_MILLIS + 1
                )
        );
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void unlockSafely(LockHandle lockHandle, String operation) {
        try {
            if (!lockService.unlock(lockHandle)) {
                log.debug("{}已过期或所有权已变化，key={}", operation, lockHandle.key());
            }
        } catch (RuntimeException exception) {
            log.warn("释放{}失败，key={}", operation, lockHandle.key(), exception);
        }
    }

    private void normalizeArticleListDtos(Collection<ArticleListDTO> articleListDtos) {
        if (articleListDtos == null) {
            return;
        }
        articleListDtos.forEach(articleListDTO -> {
            String coverUrl = articleListDTO.getCoverURL();
            if (coverUrl == null || coverUrl.isBlank()) {
                articleListDTO.setCoverURL(null);
            }
        });
    }

    private ArticleListDTO copyArticleListDto(ArticleListDTO source) {
        ArticleListDTO target = new ArticleListDTO();
        target.setId(source.getId());
        target.setArticleTitle(source.getArticleTitle());
        target.setArticleAbstract(source.getArticleAbstract());
        target.setArticleDate(source.getArticleDate());
        target.setViewCount(source.getViewCount());
        target.setArticleCover(source.getArticleCover());
        target.setCoverURL(source.getCoverURL());
        return target;
    }

    private void validatePageBounds(long totalElements, int page, int size) {
        if (totalElements <= 0) {
            return;
        }
        long maxPage = (totalElements + size - 1) / size;
        if (page > maxPage) {
            throw new ResourceNotFoundException("文章列表页不存在");
        }
    }

    /**
     * 一次列表水合的结果及其可修复状态。
     */
    record HydratedArticleList(
            List<ArticleListDTO> dtos,
            Set<Integer> confirmedMissingIds,
            Set<Integer> requestOnlyMissingIds,
            Set<Integer> unresolvedIds) {

        private static HydratedArticleList empty() {
            return new HydratedArticleList(
                    Collections.emptyList(),
                    Collections.emptySet(),
                    Collections.emptySet(),
                    Collections.emptySet()
            );
        }
    }
}
