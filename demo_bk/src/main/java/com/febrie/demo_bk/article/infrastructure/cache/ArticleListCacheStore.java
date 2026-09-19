package com.febrie.demo_bk.article.infrastructure.cache;

import com.febrie.demo_bk.article.application.dto.ArticleListDTO;
import com.febrie.demo_bk.shared.infrastructure.redis.RedisDistributedLockService.LockHandle;
import com.febrie.demo_bk.shared.infrastructure.redis.RedisStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 文章列表 DTO 正常缓存、负缓存和列表回源锁 Key 的统一访问入口。
 */
@Component
@RequiredArgsConstructor
public class ArticleListCacheStore {

    private static final String ARTICLE_LIST_KEY_PREFIX = "blog:article:list:";
    private static final String LIST_LOCK_KEY_PREFIX = "blog:lock:article:list:";
    private static final String NULL_CACHE_MARKER = "__NULL__";
    private static final long ARTICLE_LIST_TTL_MILLIS = TimeUnit.HOURS.toMillis(24L);
    private static final long NEGATIVE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(2L);
    private static final long NEGATIVE_TTL_JITTER_MILLIS = TimeUnit.SECONDS.toMillis(30L);

    private static final DefaultRedisScript<Long> PUBLISH_CACHE_SCRIPT =
            new DefaultRedisScript<>("""
                    if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                        return 0
                    end
                    redis.call('PSETEX', KEYS[2], ARGV[3], ARGV[2])
                    return 1
                    """, Long.class);

    private static final DefaultRedisScript<Long> EVICT_CACHE_SCRIPT =
            new DefaultRedisScript<>("""
                    if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                        return 0
                    end
                    redis.call('DEL', KEYS[2])
                    return 1
                    """, Long.class);

    private final RedisStore redisStore;

    public Map<Integer, CacheValue<ArticleListDTO>> getStates(List<Integer> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> keys = articleIds.stream().map(this::articleListKey).toList();
        List<Object> rawValues = redisStore.getRawObjects(keys);
        Map<Integer, CacheValue<ArticleListDTO>> states = new LinkedHashMap<>();
        for (int index = 0; index < articleIds.size(); index++) {
            states.put(articleIds.get(index), readState(rawValues.get(index)));
        }
        return states;
    }

    public boolean publish(int articleId,
                           ArticleListDTO article,
                           LockHandle lockHandle) {
        return publishCache(
                articleListKey(articleId),
                article,
                ARTICLE_LIST_TTL_MILLIS,
                lockHandle
        );
    }

    public boolean publishMissing(int articleId, LockHandle lockHandle) {
        return publishCache(
                articleListKey(articleId),
                NULL_CACHE_MARKER,
                negativeTtlMillis(),
                lockHandle
        );
    }

    public boolean evict(int articleId, LockHandle lockHandle) {
        Long result = redisStore.executeLongScript(
                EVICT_CACHE_SCRIPT,
                List.of(lockHandle.key(), articleListKey(articleId)),
                lockHandle.token()
        );
        return Long.valueOf(1L).equals(result);
    }

    public String lockKey(int articleId) {
        return LIST_LOCK_KEY_PREFIX + articleId;
    }

    private String articleListKey(int articleId) {
        return ARTICLE_LIST_KEY_PREFIX + articleId;
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

    private CacheValue<ArticleListDTO> readState(Object rawValue) {
        if (rawValue == null) {
            return CacheValue.miss();
        }
        if (rawValue instanceof String value && NULL_CACHE_MARKER.equals(value)) {
            return CacheValue.negative();
        }
        return CacheValue.hit(redisStore.convertValue(rawValue, ArticleListDTO.class));
    }

    private long negativeTtlMillis() {
        return NEGATIVE_TTL_MILLIS + ThreadLocalRandom.current().nextLong(
                NEGATIVE_TTL_JITTER_MILLIS + 1L
        );
    }
}
