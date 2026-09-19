package com.febrie.demo_bk.article.infrastructure.cache;

import com.febrie.demo_bk.article.application.dto.ArticleDTO;
import com.febrie.demo_bk.shared.infrastructure.redis.RedisDistributedLockService.LockHandle;
import com.febrie.demo_bk.shared.infrastructure.redis.RedisStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 文章详情正常缓存、负缓存和详情回源锁 Key 的统一访问入口。
 */
@Component
@RequiredArgsConstructor
public class ArticleDetailCacheStore {

    private static final String ARTICLE_DETAIL_KEY_PREFIX = "blog:article:detail:";
    private static final String DETAIL_LOCK_KEY_PREFIX = "blog:lock:article:detail:";
    private static final String NULL_CACHE_MARKER = "__NULL__";
    private static final long ARTICLE_DETAIL_TTL_MILLIS = TimeUnit.DAYS.toMillis(30L);
    private static final long NEGATIVE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(2L);
    private static final long NEGATIVE_TTL_JITTER_MILLIS = TimeUnit.SECONDS.toMillis(30L);

    /**
     * 校验锁所有权并原子发布正常缓存或负缓存。
     */
    private static final DefaultRedisScript<Long> PUBLISH_CACHE_SCRIPT =
            new DefaultRedisScript<>("""
                    if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                        return 0
                    end
                    redis.call('PSETEX', KEYS[2], ARGV[3], ARGV[2])
                    return 1
                    """, Long.class);

    /**
     * 校验锁所有权并删除正常缓存或负缓存。
     */
    private static final DefaultRedisScript<Long> EVICT_CACHE_SCRIPT =
            new DefaultRedisScript<>("""
                    if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                        return 0
                    end
                    redis.call('DEL', KEYS[2])
                    return 1
                    """, Long.class);

    private final RedisStore redisStore;

    public CacheValue<ArticleDTO> getState(int articleId) {
        Object rawValue = redisStore.getRawObject(articleDetailKey(articleId));
        if (rawValue == null) {
            return CacheValue.miss();
        }
        if (rawValue instanceof String value && NULL_CACHE_MARKER.equals(value)) {
            return CacheValue.negative();
        }
        return CacheValue.hit(redisStore.convertValue(rawValue, ArticleDTO.class));
    }

    public boolean publish(int articleId,
                           ArticleDTO article,
                           LockHandle lockHandle) {
        return publishCache(
                articleDetailKey(articleId),
                article,
                ARTICLE_DETAIL_TTL_MILLIS,
                lockHandle
        );
    }

    public boolean publishMissing(int articleId, LockHandle lockHandle) {
        return publishCache(
                articleDetailKey(articleId),
                NULL_CACHE_MARKER,
                negativeTtlMillis(),
                lockHandle
        );
    }

    public boolean evict(int articleId, LockHandle lockHandle) {
        Long result = redisStore.executeLongScript(
                EVICT_CACHE_SCRIPT,
                List.of(lockHandle.key(), articleDetailKey(articleId)),
                lockHandle.token()
        );
        return Long.valueOf(1L).equals(result);
    }

    public String lockKey(int articleId) {
        return DETAIL_LOCK_KEY_PREFIX + articleId;
    }

    private String articleDetailKey(int articleId) {
        return ARTICLE_DETAIL_KEY_PREFIX + articleId;
    }

    private boolean publishCache(String cacheKey,
                                 Object value,
                                 long ttlMillis,
                                 LockHandle lockHandle) {
        Long result = redisStore.executeLongScript(
                PUBLISH_CACHE_SCRIPT,
                List.of(lockHandle.key(), cacheKey),
                lockHandle.token(),
                redisStore.serializeValue(value),
                String.valueOf(ttlMillis)
        );
        return Long.valueOf(1L).equals(result);
    }

    private long negativeTtlMillis() {
        return NEGATIVE_TTL_MILLIS + ThreadLocalRandom.current().nextLong(
                NEGATIVE_TTL_JITTER_MILLIS + 1L
        );
    }
}
