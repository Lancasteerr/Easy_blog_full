package com.febrie.demo_bk.article.application.query;

import com.febrie.demo_bk.article.application.ArticleConverter;
import com.febrie.demo_bk.article.application.ArticleViewService;
import com.febrie.demo_bk.article.application.dto.ArticleDTO;
import com.febrie.demo_bk.article.infrastructure.cache.ArticleDetailCacheStore;
import com.febrie.demo_bk.article.infrastructure.cache.CacheValue;
import com.febrie.demo_bk.article.infrastructure.cache.CacheValue.CacheState;
import com.febrie.demo_bk.article.infrastructure.persistence.ArticleMapper;
import com.febrie.demo_bk.article.infrastructure.persistence.BlogArticle;
import com.febrie.demo_bk.shared.error.ResourceNotFoundException;
import com.febrie.demo_bk.shared.infrastructure.redis.RedisDistributedLockService;
import com.febrie.demo_bk.shared.infrastructure.redis.RedisDistributedLockService.LockHandle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 文章详情查询，封装详情缓存、负缓存、并发回源锁和 MySQL 降级。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleDetailQuery {

    private static final Duration CACHE_LOCK_LEASE = Duration.ofSeconds(10L);
    private static final Duration CACHE_WAIT = Duration.ofMillis(800L);

    private final ArticleMapper articleMapper;
    private final ArticleDetailCacheStore detailCacheStore;
    private final ArticleViewService articleViewService;
    private final RedisDistributedLockService lockService;

    /**
     * 按 ID 查询文章，并在成功返回前记录本次浏览。
     */
    public ArticleDTO findById(int articleId) {
        CacheValue<ArticleDTO> cachedState;
        try {
            cachedState = detailCacheStore.getState(articleId);
        } catch (RuntimeException exception) {
            log.warn("读取文章详情缓存失败，直接回退MySQL，articleId={}", articleId, exception);
            return loadWithoutCache(articleId);
        }

        ArticleDTO cachedResult = resolveCacheState(articleId, cachedState);
        if (cachedResult != null) {
            return cachedResult;
        }

        LockHandle lockHandle;
        try {
            lockHandle = lockService.tryLock(
                    detailCacheStore.lockKey(articleId),
                    CACHE_LOCK_LEASE,
                    CACHE_WAIT
            );
        } catch (RuntimeException exception) {
            log.warn("获取文章详情回源锁失败，直接回退MySQL，articleId={}", articleId, exception);
            return loadWithoutCache(articleId);
        }

        if (lockHandle == null) {
            return loadAfterLockTimeout(articleId);
        }

        try {
            // 获锁后必须二次检查，避免重复回源覆盖其他请求刚发布的状态。
            CacheValue<ArticleDTO> secondState;
            try {
                secondState = detailCacheStore.getState(articleId);
            } catch (RuntimeException exception) {
                log.warn("详情获锁后二次读取缓存失败，直接回退MySQL，articleId={}", articleId, exception);
                return loadWithoutCache(articleId);
            }
            ArticleDTO secondResult = resolveCacheState(articleId, secondState);
            if (secondResult != null) {
                return secondResult;
            }

            BlogArticle article = articleMapper.selectById(articleId);
            if (article == null) {
                try {
                    if (!detailCacheStore.publishMissing(articleId, lockHandle)) {
                        log.warn("文章详情负缓存发布被拒绝，articleId={}", articleId);
                    }
                } catch (RuntimeException exception) {
                    log.warn("文章详情负缓存发布失败，articleId={}", articleId, exception);
                }
                throw new ResourceNotFoundException("文章不存在");
            }

            ArticleDTO articleDTO = ArticleConverter.toDto(article);
            try {
                if (!detailCacheStore.publish(articleId, articleDTO, lockHandle)) {
                    // 缓存发布失败不影响已经从数据库取得的当前响应。
                    log.warn("文章详情正常缓存发布被拒绝，articleId={}", articleId);
                }
            } catch (RuntimeException exception) {
                log.warn("文章详情正常缓存发布失败，articleId={}", articleId, exception);
            }
            return recordViewAndReturn(articleId, articleDTO);
        } finally {
            unlockSafely(lockHandle);
        }
    }

    private ArticleDTO resolveCacheState(int articleId, CacheValue<ArticleDTO> state) {
        if (state.state() == CacheState.NEGATIVE) {
            throw new ResourceNotFoundException("文章不存在");
        }
        if (state.state() == CacheState.HIT) {
            return recordViewAndReturn(articleId, state.value());
        }
        return null;
    }

    private ArticleDTO loadAfterLockTimeout(int articleId) {
        try {
            CacheValue<ArticleDTO> finalState = detailCacheStore.getState(articleId);
            ArticleDTO cached = resolveCacheState(articleId, finalState);
            if (cached != null) {
                return cached;
            }
        } catch (ResourceNotFoundException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("详情锁等待后重读缓存失败，articleId={}", articleId, exception);
        }
        return loadWithoutCache(articleId);
    }

    private ArticleDTO loadWithoutCache(int articleId) {
        BlogArticle article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new ResourceNotFoundException("文章不存在");
        }
        return recordViewAndReturn(articleId, ArticleConverter.toDto(article));
    }

    private ArticleDTO recordViewAndReturn(int articleId, ArticleDTO articleDTO) {
        Long realtimeViewCount = articleViewService.recordView(
                (long) articleId,
                articleDTO.getViewCount()
        );
        if (realtimeViewCount != null) {
            articleDTO.setViewCount(realtimeViewCount);
        }
        return articleDTO;
    }

    private void unlockSafely(LockHandle lockHandle) {
        try {
            if (!lockService.unlock(lockHandle)) {
                log.debug("文章详情回源锁已过期或所有权已变化，key={}", lockHandle.key());
            }
        } catch (RuntimeException exception) {
            log.warn("释放文章详情回源锁失败，key={}", lockHandle.key(), exception);
        }
    }
}
