package com.febrie.demo_bk.article.application.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.febrie.demo_bk.article.application.dto.ArticleListDTO;
import com.febrie.demo_bk.article.application.query.ArticleListHydrator.HydratedArticleList;
import com.febrie.demo_bk.article.infrastructure.cache.ArticlePageIndex;
import com.febrie.demo_bk.article.infrastructure.cache.LatestArticleIndexStore;
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
import java.util.concurrent.atomic.AtomicLong;

/**
 * 最新文章列表查询，负责版本化分页索引、失效索引修复和完整分页降级。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LatestArticleListQuery {

    private static final int MAX_LATEST_VERSION_ATTEMPTS = 2;
    private static final Duration CACHE_LOCK_LEASE = Duration.ofSeconds(10L);
    private static final Duration CACHE_WAIT = Duration.ofMillis(800L);

    private final ArticleMapper articleMapper;
    private final LatestArticleIndexStore latestIndexStore;
    private final ArticleListHydrator articleListHydrator;
    private final RedisDistributedLockService lockService;

    /**
     * 没有引入额外监控依赖时，计数随结构化告警日志一同输出。
     */
    private final AtomicLong latestRepairExhaustedCount = new AtomicLong();

    /**
     * 查询最新文章；最多读取两个 Redis 版本，仍失效时回退 MySQL 完整分页。
     */
    public PageResult<ArticleListDTO> findPage(int page, int size) {
        long sourceVersion;
        try {
            sourceVersion = latestIndexStore.getVersion();
        } catch (RuntimeException exception) {
            log.warn("读取最新文章版本失败，回退MySQL完整分页", exception);
            return articleListHydrator.loadLatestMysqlPage(page, size);
        }

        for (int attempt = 0; attempt < MAX_LATEST_VERSION_ATTEMPTS; attempt++) {
            LatestIndexLoad indexLoad;
            try {
                indexLoad = loadPageIndex(sourceVersion, page, size);
            } catch (RuntimeException exception) {
                log.warn("加载最新文章分页索引失败，回退MySQL完整分页", exception);
                return articleListHydrator.loadLatestMysqlPage(page, size);
            }

            if (indexLoad.redirectVersion() != null) {
                sourceVersion = indexLoad.redirectVersion();
                continue;
            }

            ArticlePageIndex pageIndex = indexLoad.pageIndex();
            validatePageBounds(pageIndex.getTotalElements(), page, size);
            HydratedArticleList hydrated = articleListHydrator.hydrate(
                    pageIndex.getIds(),
                    null
            );

            boolean confirmedInvalid = !hydrated.confirmedMissingIds().isEmpty();
            if (confirmedInvalid) {
                try {
                    sourceVersion = latestIndexStore.advanceVersion(sourceVersion);
                } catch (RuntimeException exception) {
                    log.warn("推进失效的最新文章版本失败，回退MySQL完整分页", exception);
                    return articleListHydrator.loadLatestMysqlPage(page, size);
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
                return articleListHydrator.loadLatestMysqlPage(page, size);
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
                return articleListHydrator.loadLatestMysqlPage(page, size);
            }
        }

        // 两次都只遇到版本跳转时，不继续追逐快速变化的版本。
        log.warn("最新文章版本连续变化，回退MySQL完整分页，page={}，size={}", page, size);
        return articleListHydrator.loadLatestMysqlPage(page, size);
    }

    /**
     * 使用版本分页锁合并索引回源；未获得锁时查询当前快照但不发布缓存。
     */
    private LatestIndexLoad loadPageIndex(long sourceVersion, int page, int size) {
        ArticlePageIndex cached = latestIndexStore.getPageIndex(sourceVersion, page, size);
        if (cached != null) {
            long currentVersion = latestIndexStore.getVersion();
            if (currentVersion != sourceVersion) {
                return LatestIndexLoad.redirect(currentVersion);
            }
            return LatestIndexLoad.loaded(sourceVersion, cached);
        }

        LockHandle lockHandle = lockService.tryLock(
                latestIndexStore.pageLockKey(sourceVersion, page, size),
                CACHE_LOCK_LEASE,
                CACHE_WAIT
        );
        if (lockHandle == null) {
            long currentVersion = latestIndexStore.getVersion();
            if (currentVersion != sourceVersion) {
                return LatestIndexLoad.redirect(currentVersion);
            }

            ArticlePageIndex secondRead = latestIndexStore.getPageIndex(
                    sourceVersion,
                    page,
                    size
            );
            return LatestIndexLoad.loaded(
                    sourceVersion,
                    secondRead == null ? queryPageIndex(page, size) : secondRead
            );
        }

        try {
            long currentVersion = latestIndexStore.getVersion();
            if (currentVersion != sourceVersion) {
                return LatestIndexLoad.redirect(currentVersion);
            }

            ArticlePageIndex secondRead = latestIndexStore.getPageIndex(
                    sourceVersion,
                    page,
                    size
            );
            if (secondRead != null) {
                return LatestIndexLoad.loaded(sourceVersion, secondRead);
            }

            ArticlePageIndex databaseIndex = queryPageIndex(page, size);
            long publishResult = latestIndexStore.publishPageIndex(
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
            unlockSafely(lockHandle);
        }
    }

    private ArticlePageIndex queryPageIndex(int page, int size) {
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

    private void validatePageBounds(long totalElements, int page, int size) {
        if (totalElements <= 0) {
            return;
        }
        long maxPage = (totalElements + size - 1) / size;
        if (page > maxPage) {
            throw new ResourceNotFoundException("文章列表页不存在");
        }
    }

    private void unlockSafely(LockHandle lockHandle) {
        try {
            if (!lockService.unlock(lockHandle)) {
                log.debug("最新文章分页索引锁已过期或所有权已变化，key={}", lockHandle.key());
            }
        } catch (RuntimeException exception) {
            log.warn("释放最新文章分页索引锁失败，key={}", lockHandle.key(), exception);
        }
    }

    private record LatestIndexLoad(long version,
                                   ArticlePageIndex pageIndex,
                                   Long redirectVersion) {

        private static LatestIndexLoad loaded(long version, ArticlePageIndex pageIndex) {
            return new LatestIndexLoad(version, pageIndex, null);
        }

        private static LatestIndexLoad redirect(long version) {
            return new LatestIndexLoad(0L, null, version);
        }
    }
}
