package com.febrie.demo_bk.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.febrie.demo_bk.article.internal.ArticleCacheStore;
import com.febrie.demo_bk.article.internal.ArticleCacheStore.CacheState;
import com.febrie.demo_bk.article.internal.ArticleCacheStore.CacheValue;
import com.febrie.demo_bk.article.internal.ArticleMapper;
import com.febrie.demo_bk.article.internal.ArticlePageIndex;
import com.febrie.demo_bk.article.internal.ArticleViewService;
import com.febrie.demo_bk.article.internal.ArticleViewStore;
import com.febrie.demo_bk.article.internal.BlogArticle;
import com.febrie.demo_bk.shared.infrastructure.RedisDistributedLockService;
import com.febrie.demo_bk.shared.infrastructure.RedisDistributedLockService.LockHandle;
import com.febrie.demo_bk.shared.web.PageResult;
import com.febrie.demo_bk.shared.web.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
import java.util.concurrent.atomic.AtomicLong;

/**
 * 文章读取用例入口，统一处理缓存旁路、并发回源、分页修复和 Redis 故障降级。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleQueryService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_LATEST_VERSION_ATTEMPTS = 2;
    private static final int MAX_RANK_HYDRATE_ATTEMPTS = 2;
    private static final int MIN_CACHE_POLL_MILLIS = 30;
    private static final int MAX_CACHE_POLL_MILLIS = 80;

    private static final Duration CACHE_LOCK_LEASE = Duration.ofSeconds(10L);
    private static final Duration CACHE_WAIT = Duration.ofMillis(800L);

    private final ArticleMapper articleMapper;
    private final ArticleCacheStore articleCacheStore;
    private final ArticleViewStore articleViewStore;
    private final ArticleViewService articleViewService;
    private final RedisDistributedLockService lockService;

    /**
     * 没有引入额外监控依赖时，计数会随结构化告警日志一同输出。
     */
    private final AtomicLong rankRepairExhaustedCount = new AtomicLong();
    private final AtomicLong latestRepairExhaustedCount = new AtomicLong();

    /**
     * 详情缓存使用 DTO、负缓存和真正未命中三态，并通过回源锁合并并发查询。
     */
    public ArticleDTO findById(int articleId) {
        validateArticleId(articleId);

        CacheValue<ArticleDTO> cachedState;
        try {
            cachedState = articleCacheStore.getArticleDetailState(articleId);
        } catch (RuntimeException exception) {
            log.warn("读取文章详情缓存失败，直接回退MySQL，articleId={}", articleId, exception);
            return loadDetailWithoutCache(articleId);
        }

        ArticleDTO cachedResult = resolveDetailCacheState(articleId, cachedState);
        if (cachedResult != null) {
            return cachedResult;
        }

        LockHandle lockHandle;
        try {
            lockHandle = lockService.tryLock(
                    articleCacheStore.articleDetailLockKey(articleId),
                    CACHE_LOCK_LEASE,
                    CACHE_WAIT
            );
        } catch (RuntimeException exception) {
            log.warn("获取文章详情回源锁失败，直接回退MySQL，articleId={}", articleId, exception);
            return loadDetailWithoutCache(articleId);
        }

        if (lockHandle == null) {
            return loadDetailAfterLockTimeout(articleId);
        }

        try {
            // 获锁后必须二次检查，避免重复回源覆盖其他请求刚发布的状态。
            CacheValue<ArticleDTO> secondState;
            try {
                secondState = articleCacheStore.getArticleDetailState(articleId);
            } catch (RuntimeException exception) {
                log.warn("详情获锁后二次读取缓存失败，直接回退MySQL，articleId={}", articleId, exception);
                return loadDetailWithoutCache(articleId);
            }
            ArticleDTO secondResult = resolveDetailCacheState(articleId, secondState);
            if (secondResult != null) {
                return secondResult;
            }

            BlogArticle article = articleMapper.selectById(articleId);
            if (article == null) {
                try {
                    if (!articleCacheStore.publishMissingArticleDetail(articleId, lockHandle)) {
                        log.warn("文章详情负缓存发布被拒绝，articleId={}", articleId);
                    }
                } catch (RuntimeException exception) {
                    log.warn("文章详情负缓存发布失败，articleId={}", articleId, exception);
                }
                throw new ResourceNotFoundException("文章不存在");
            }

            ArticleDTO articleDTO = ArticleConverter.toDto(article);
            try {
                if (!articleCacheStore.publishArticleDetail(articleId, articleDTO, lockHandle)) {
                    // 缓存发布失败不影响已经从数据库取得的当前响应。
                    log.warn("文章详情正常缓存发布被拒绝，articleId={}", articleId);
                }
            } catch (RuntimeException exception) {
                log.warn("文章详情正常缓存发布失败，articleId={}", articleId, exception);
            }
            return recordViewAndReturn(articleId, articleDTO);
        } finally {
            unlockSafely(lockHandle, "文章详情回源锁");
        }
    }

    public PageResult<ArticleListDTO> findPage(int page,
                                               int size,
                                               String sort) {
        validatePageParams(page, size);
        return "viewCountDesc".equals(normalizeSort(sort))
                ? getViewRankArticleList(page, size)
                : getLatestArticleList(page, size);
    }

    /**
     * 最新列表最多读取两个 Redis 版本，第二个版本仍失效时直接回退完整 DTO 分页。
     */
    private PageResult<ArticleListDTO> getLatestArticleList(int page, int size) {
        long sourceVersion;
        try {
            sourceVersion = articleCacheStore.getLatestVersion();
        } catch (RuntimeException exception) {
            log.warn("读取最新文章版本失败，回退MySQL完整分页", exception);
            return getMysqlArticleListPage(page, size, ArticleSort.LATEST, true);
        }

        for (int attempt = 0; attempt < MAX_LATEST_VERSION_ATTEMPTS; attempt++) {
            LatestIndexLoad indexLoad;
            try {
                indexLoad = loadLatestPageIndex(sourceVersion, page, size);
            } catch (RuntimeException exception) {
                log.warn("加载最新文章分页索引失败，回退MySQL完整分页", exception);
                return getMysqlArticleListPage(page, size, ArticleSort.LATEST, true);
            }

            if (indexLoad.redirectVersion() != null) {
                sourceVersion = indexLoad.redirectVersion();
                continue;
            }

            ArticlePageIndex pageIndex = indexLoad.pageIndex();
            validatePageBounds(pageIndex.getTotalElements(), page, size);
            HydratedArticleList hydrated = hydrateArticleListDtos(
                    pageIndex.getIds(),
                    null
            );

            boolean confirmedInvalid = !hydrated.confirmedMissingIds().isEmpty();
            if (confirmedInvalid) {
                try {
                    sourceVersion = articleCacheStore.advanceLatestVersion(sourceVersion);
                } catch (RuntimeException exception) {
                    log.warn("推进失效的最新文章版本失败，回退MySQL完整分页", exception);
                    return getMysqlArticleListPage(page, size, ArticleSort.LATEST, true);
                }
            }

            if (!hydrated.unresolvedIds().isEmpty()
                    || !hydrated.requestOnlyMissingIds().isEmpty()) {
                log.warn(
                        "最新列表存在不可修复ID，回退MySQL完整分页，version={}，requestOnly={}，unresolved={}",
                        indexLoad.version(),
                        hydrated.requestOnlyMissingIds(),
                        hydrated.unresolvedIds()
                );
                return getMysqlArticleListPage(page, size, ArticleSort.LATEST, true);
            }

            if (!confirmedInvalid) {
                return new PageResult<>(
                        hydrated.dtos(),
                        pageIndex.getTotalElements(),
                        pageIndex.getNumber()
                );
            }

            if (attempt == MAX_LATEST_VERSION_ATTEMPTS - 1) {
                long count = latestRepairExhaustedCount.incrementAndGet();
                log.warn(
                        "metric=latest_repair_exhausted count={} version={} page={} size={} ids={}",
                        count,
                        indexLoad.version(),
                        page,
                        size,
                        hydrated.confirmedMissingIds()
                );
                return getMysqlArticleListPage(page, size, ArticleSort.LATEST, true);
            }
        }

        // 两次都只遇到版本跳转时，不继续追逐快速变化的版本。
        log.warn("最新文章版本连续变化，回退MySQL完整分页，page={}，size={}", page, size);
        return getMysqlArticleListPage(page, size, ArticleSort.LATEST, true);
    }

    /**
     * 浏览量榜最多重读一次 ZSet，第二轮仍有失效成员时删除后返回短页。
     */
    private PageResult<ArticleListDTO> getViewRankArticleList(int page, int size) {
        try {
            if (!articleViewStore.hasRankIndex()) {
                return getMysqlArticleListPage(page, size, ArticleSort.VIEW_COUNT, false);
            }
        } catch (RuntimeException exception) {
            log.warn("检查文章排行榜失败，回退MySQL快照", exception);
            return getMysqlArticleListPage(page, size, ArticleSort.VIEW_COUNT, false);
        }

        for (int attempt = 0; attempt < MAX_RANK_HYDRATE_ATTEMPTS; attempt++) {
            ArticleViewStore.RankPage rankPage;
            try {
                rankPage = articleViewStore.getRankPage(page, size);
            } catch (RuntimeException exception) {
                log.warn("读取文章排行榜失败，回退MySQL快照", exception);
                return getMysqlArticleListPage(page, size, ArticleSort.VIEW_COUNT, false);
            }

            validatePageBounds(rankPage.totalElements(), page, size);
            HydratedArticleList hydrated = hydrateArticleListDtos(
                    rankPage.ids(),
                    rankPage.scores()
            );

            if (!hydrated.confirmedMissingIds().isEmpty()) {
                try {
                    articleViewStore.removeRankMembers(hydrated.confirmedMissingIds());
                } catch (RuntimeException exception) {
                    log.warn("清理排行榜失效成员失败，回退MySQL快照", exception);
                    return getMysqlArticleListPage(page, size, ArticleSort.VIEW_COUNT, false);
                }
            }

            if (!hydrated.unresolvedIds().isEmpty()) {
                log.warn(
                        "排行榜存在未解决ID，回退MySQL快照，page={}，size={}，ids={}",
                        page,
                        size,
                        hydrated.unresolvedIds()
                );
                return getMysqlArticleListPage(page, size, ArticleSort.VIEW_COUNT, false);
            }

            if (hydrated.confirmedMissingIds().isEmpty()) {
                return new PageResult<>(hydrated.dtos(), rankPage.totalElements(), page);
            }

            if (attempt == MAX_RANK_HYDRATE_ATTEMPTS - 1) {
                long totalElements;
                try {
                    totalElements = articleViewStore.getRankSize();
                } catch (RuntimeException exception) {
                    log.warn("读取清理后的排行榜总数失败，回退MySQL快照", exception);
                    return getMysqlArticleListPage(page, size, ArticleSort.VIEW_COUNT, false);
                }
                validatePageBounds(totalElements, page, size);
                long count = rankRepairExhaustedCount.incrementAndGet();
                log.warn(
                        "metric=rank_repair_exhausted count={} page={} size={} ids={}",
                        count,
                        page,
                        size,
                        hydrated.confirmedMissingIds()
                );
                return new PageResult<>(hydrated.dtos(), totalElements, page);
            }
        }

        throw new IllegalStateException("排行榜修复流程未产生结果");
    }

    /**
     * 使用版本分页锁合并索引回源；未获得锁时可查询当前请求快照但不发布缓存。
     */
    private LatestIndexLoad loadLatestPageIndex(long sourceVersion,
                                                int page,
                                                int size) {
        ArticlePageIndex cached = articleCacheStore.getLatestPageIndex(
                sourceVersion,
                page,
                size
        );
        if (cached != null) {
            long currentVersion = articleCacheStore.getLatestVersion();
            if (currentVersion != sourceVersion) {
                return LatestIndexLoad.redirect(currentVersion);
            }
            return LatestIndexLoad.loaded(sourceVersion, cached);
        }

        LockHandle lockHandle = lockService.tryLock(
                articleCacheStore.latestPageLockKey(sourceVersion, page, size),
                CACHE_LOCK_LEASE,
                CACHE_WAIT
        );
        if (lockHandle == null) {
            long currentVersion = articleCacheStore.getLatestVersion();
            if (currentVersion != sourceVersion) {
                return LatestIndexLoad.redirect(currentVersion);
            }

            ArticlePageIndex secondRead = articleCacheStore.getLatestPageIndex(
                    sourceVersion,
                    page,
                    size
            );
            return LatestIndexLoad.loaded(
                    sourceVersion,
                    secondRead == null ? queryLatestPageIndex(page, size) : secondRead
            );
        }

        try {
            long currentVersion = articleCacheStore.getLatestVersion();
            if (currentVersion != sourceVersion) {
                return LatestIndexLoad.redirect(currentVersion);
            }

            ArticlePageIndex secondRead = articleCacheStore.getLatestPageIndex(
                    sourceVersion,
                    page,
                    size
            );
            if (secondRead != null) {
                return LatestIndexLoad.loaded(sourceVersion, secondRead);
            }

            ArticlePageIndex databaseIndex = queryLatestPageIndex(page, size);
            long publishResult = articleCacheStore.publishLatestPageIndex(
                    sourceVersion,
                    page,
                    size,
                    databaseIndex,
                    lockHandle
            );
            if (publishResult > 0L && publishResult != sourceVersion) {
                return LatestIndexLoad.redirect(publishResult);
            }
            if (publishResult == -1L) {
                log.warn(
                        "分页索引锁已失效，本次结果仅用于当前请求，version={}，page={}，size={}",
                        sourceVersion,
                        page,
                        size
                );
            }
            return LatestIndexLoad.loaded(sourceVersion, databaseIndex);
        } finally {
            unlockSafely(lockHandle, "最新文章分页索引锁");
        }
    }

    private ArticlePageIndex queryLatestPageIndex(int page, int size) {
        LambdaQueryWrapper<BlogArticle> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper
                .orderByDesc(BlogArticle::getArticleDate)
                .orderByDesc(BlogArticle::getId);
        Page<Integer> result = articleMapper.selectArticleIdPage(
                new Page<>(page, size),
                queryWrapper
        );
        return new ArticlePageIndex(result.getRecords(), result.getTotal(), page, size);
    }

    /**
     * Redis 状态无法确认时直接查询完整 DTO 页面，不再经过 ID 索引和 DTO 缓存。
     */
    private PageResult<ArticleListDTO> getMysqlArticleListPage(int page,
                                                               int size,
                                                               ArticleSort sort,
                                                               boolean applyRealtimeScores) {
        LambdaQueryWrapper<BlogArticle> queryWrapper = new LambdaQueryWrapper<>();
        if (sort == ArticleSort.VIEW_COUNT) {
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

    /**
     * 批量加载 DTO，并区分可修复的确认不存在、仅本请求不存在和未知状态。
     */
    private HydratedArticleList hydrateArticleListDtos(List<Integer> articleIds,
                                                        Map<Integer, Long> rankScores) {
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
                    articleCacheStore.getArticleListDtoStates(new ArrayList<>(uniqueIds));
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
                        articleCacheStore.articleListLockKey(articleId),
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
                            if (!articleCacheStore.publishArticleListDto(
                                    articleId,
                                    dto,
                                    lockHandle
                            )) {
                                log.warn("文章DTO正常缓存发布被拒绝，articleId={}", articleId);
                            }
                        } catch (RuntimeException exception) {
                            // 已查询到的 DTO 仍可用于当前响应，缓存失败只记录日志。
                            log.warn("发布文章DTO正常缓存失败，articleId={}", articleId, exception);
                        }
                    } else {
                        publishMissingListState(
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
                        articleCacheStore.getArticleListDtoStates(new ArrayList<>(pendingIds));
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

    private void publishMissingListState(
            int articleId,
            LockHandle lockHandle,
            Set<Integer> confirmedMissingIds,
            Set<Integer> requestOnlyMissingIds) {
        try {
            if (articleCacheStore.publishMissingArticleListDto(articleId, lockHandle)) {
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

    private ArticleDTO resolveDetailCacheState(int articleId,
                                               CacheValue<ArticleDTO> state) {
        if (state.state() == CacheState.NEGATIVE) {
            throw new ResourceNotFoundException("文章不存在");
        }
        if (state.state() == CacheState.HIT) {
            return recordViewAndReturn(articleId, state.value());
        }
        return null;
    }

    private ArticleDTO loadDetailAfterLockTimeout(int articleId) {
        try {
            CacheValue<ArticleDTO> finalState =
                    articleCacheStore.getArticleDetailState(articleId);
            ArticleDTO cached = resolveDetailCacheState(articleId, finalState);
            if (cached != null) {
                return cached;
            }
        } catch (ResourceNotFoundException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("详情锁等待后重读缓存失败，articleId={}", articleId, exception);
        }
        return loadDetailWithoutCache(articleId);
    }

    private ArticleDTO loadDetailWithoutCache(int articleId) {
        BlogArticle article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new ResourceNotFoundException("文章不存在");
        }
        return recordViewAndReturn(articleId, ArticleConverter.toDto(article));
    }

    private ArticleDTO recordViewAndReturn(int articleId, ArticleDTO articleDTO) {
        applyRealtimeViewCount(
                articleDTO,
                articleViewService.recordView((long) articleId, articleDTO.getViewCount())
        );
        return articleDTO;
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

    private void validateArticleId(int articleId) {
        if (articleId <= 0) {
            throw new IllegalArgumentException("文章ID不合法");
        }
    }

    private void validatePageParams(int page, int size) {
        if (page < 1) {
            throw new IllegalArgumentException("页码不能小于1");
        }
        if (size < 1) {
            throw new IllegalArgumentException("每页数量不能小于1");
        }
        if (size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("每页数量不能超过" + MAX_PAGE_SIZE);
        }
    }

    private void validatePageBounds(long totalElements,
                                    int page,
                                    int size) {
        if (totalElements <= 0) {
            return;
        }

        long maxPage = (totalElements + size - 1) / size;
        if (page > maxPage) {
            throw new ResourceNotFoundException("文章列表页不存在");
        }
    }

    private String normalizeSort(String sort) {
        return "viewCountDesc".equals(sort) ? sort : "default";
    }

    private void applyRealtimeViewCount(ArticleDTO articleDTO,
                                        Long realtimeViewCount) {
        if (articleDTO != null && realtimeViewCount != null) {
            articleDTO.setViewCount(realtimeViewCount);
        }
    }

    private enum ArticleSort {
        LATEST,
        VIEW_COUNT
    }

    private record LatestIndexLoad(long version,
                                   ArticlePageIndex pageIndex,
                                   Long redirectVersion) {

        private static LatestIndexLoad loaded(long version,
                                              ArticlePageIndex pageIndex) {
            return new LatestIndexLoad(version, pageIndex, null);
        }

        private static LatestIndexLoad redirect(long version) {
            return new LatestIndexLoad(0L, null, version);
        }
    }

    private record HydratedArticleList(
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
