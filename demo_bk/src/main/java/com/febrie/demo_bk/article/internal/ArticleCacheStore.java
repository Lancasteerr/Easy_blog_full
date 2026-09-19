package com.febrie.demo_bk.article.internal;

import com.febrie.demo_bk.article.ArticleDTO;
import com.febrie.demo_bk.article.ArticleListDTO;
import com.febrie.demo_bk.shared.infrastructure.RedisDistributedLockService.LockHandle;
import com.febrie.demo_bk.shared.infrastructure.RedisStore;
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
 * 文章详情、列表索引和列表展示对象的 Redis 访问入口。
 */
@Component
@RequiredArgsConstructor
public class ArticleCacheStore {

    private static final String ARTICLE_DETAIL_KEY_PREFIX = "blog:article:detail:";
    private static final String LATEST_VERSION_KEY = "blog:article:index:latest:version";
    private static final String LATEST_PAGE_KEY_PREFIX = "blog:article:index:latest:";
    private static final String ARTICLE_LIST_KEY_PREFIX = "blog:article:list:";

    private static final String DETAIL_LOCK_KEY_PREFIX = "blog:lock:article:detail:";
    private static final String LIST_LOCK_KEY_PREFIX = "blog:lock:article:list:";
    private static final String LATEST_PAGE_LOCK_KEY_PREFIX =
            "blog:lock:article:index:latest:";

    private static final String NULL_CACHE_MARKER = "__NULL__";
    private static final long ARTICLE_DETAIL_TTL_MILLIS = TimeUnit.DAYS.toMillis(30L);
    private static final long LATEST_PAGE_TTL_MILLIS = TimeUnit.HOURS.toMillis(1L);
    private static final long ARTICLE_LIST_TTL_MILLIS = TimeUnit.HOURS.toMillis(24L);
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

    /**
     * 分页索引只能写入当前版本，并且调用者必须仍持有索引锁。
     */
    private static final DefaultRedisScript<Long> PUBLISH_PAGE_INDEX_SCRIPT =
            new DefaultRedisScript<>("""
                    local current = tonumber(redis.call('GET', KEYS[1]) or '1')
                    if current ~= tonumber(ARGV[1]) then
                        return current
                    end
                    if redis.call('GET', KEYS[3]) ~= ARGV[2] then
                        return -1
                    end
                    redis.call('PSETEX', KEYS[2], ARGV[4], ARGV[3])
                    return current
                    """, Long.class);

    /**
     * 只有仍使用期望版本的请求可以推进一次版本。
     */
    private static final DefaultRedisScript<Long> ADVANCE_VERSION_SCRIPT =
            new DefaultRedisScript<>("""
                    local value = redis.call('GET', KEYS[1])
                    local current = value and tonumber(value) or 1
                    local expected = tonumber(ARGV[1])
                    if not value then
                        redis.call('SET', KEYS[1], current)
                    end
                    if current == expected then
                        return redis.call('INCR', KEYS[1])
                    end
                    return current
                    """, Long.class);

    /**
     * 每个已提交的文章变更都必须独立推进一次全局版本。
     */
    private static final DefaultRedisScript<Long> INCREASE_VERSION_SCRIPT =
            new DefaultRedisScript<>("""
                    if not redis.call('GET', KEYS[1]) then
                        redis.call('SET', KEYS[1], 1)
                    end
                    return redis.call('INCR', KEYS[1])
                    """, Long.class);

    private final RedisStore redisStore;

    public CacheValue<ArticleDTO> getArticleDetailState(int articleId) {
        return readState(
                redisStore.getRawObject(articleDetailKey(articleId)),
                ArticleDTO.class
        );
    }

    public Map<Integer, CacheValue<ArticleListDTO>> getArticleListDtoStates(
            List<Integer> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> keys = articleIds.stream().map(this::articleListKey).toList();
        List<Object> rawValues = redisStore.getRawObjects(keys);
        Map<Integer, CacheValue<ArticleListDTO>> states = new LinkedHashMap<>();
        for (int index = 0; index < articleIds.size(); index++) {
            states.put(
                    articleIds.get(index),
                    readState(rawValues.get(index), ArticleListDTO.class)
            );
        }
        return states;
    }

    public boolean publishArticleDetail(int articleId,
                                        ArticleDTO article,
                                        LockHandle lockHandle) {
        return publishCache(
                articleDetailKey(articleId),
                article,
                ARTICLE_DETAIL_TTL_MILLIS,
                lockHandle
        );
    }

    public boolean publishMissingArticleDetail(int articleId,
                                               LockHandle lockHandle) {
        return publishCache(
                articleDetailKey(articleId),
                NULL_CACHE_MARKER,
                negativeTtlMillis(),
                lockHandle
        );
    }

    public boolean publishArticleListDto(int articleId,
                                         ArticleListDTO article,
                                         LockHandle lockHandle) {
        return publishCache(
                articleListKey(articleId),
                article,
                ARTICLE_LIST_TTL_MILLIS,
                lockHandle
        );
    }

    public boolean publishMissingArticleListDto(int articleId,
                                                LockHandle lockHandle) {
        return publishCache(
                articleListKey(articleId),
                NULL_CACHE_MARKER,
                negativeTtlMillis(),
                lockHandle
        );
    }

    public boolean evictArticleDetail(int articleId, LockHandle lockHandle) {
        return evictCache(articleDetailKey(articleId), lockHandle);
    }

    public boolean evictArticleListDto(int articleId, LockHandle lockHandle) {
        return evictCache(articleListKey(articleId), lockHandle);
    }

    public long getLatestVersion() {
        redisStore.setIfAbsent(LATEST_VERSION_KEY, 1L);
        Long version = redisStore.getObject(LATEST_VERSION_KEY, Long.class);
        return version == null ? 1L : version;
    }

    public long increaseLatestVersion() {
        Long result = redisStore.executeLongScript(
                INCREASE_VERSION_SCRIPT,
                List.of(LATEST_VERSION_KEY)
        );
        return requireScriptResult(result, "推进最新文章列表版本");
    }

    public long advanceLatestVersion(long sourceVersion) {
        Long result = redisStore.executeLongScript(
                ADVANCE_VERSION_SCRIPT,
                List.of(LATEST_VERSION_KEY),
                String.valueOf(sourceVersion)
        );
        return requireScriptResult(result, "条件推进最新文章列表版本");
    }

    public String latestPageKey(long version, int page, int size) {
        return LATEST_PAGE_KEY_PREFIX + version + ":" + page + ":" + size;
    }

    public String latestPageLockKey(long version, int page, int size) {
        return LATEST_PAGE_LOCK_KEY_PREFIX + version + ":" + page + ":" + size;
    }

    public String articleDetailLockKey(int articleId) {
        return DETAIL_LOCK_KEY_PREFIX + articleId;
    }

    public String articleListLockKey(int articleId) {
        return LIST_LOCK_KEY_PREFIX + articleId;
    }

    private String articleDetailKey(int articleId) {
        return ARTICLE_DETAIL_KEY_PREFIX + articleId;
    }

    private String articleListKey(int articleId) {
        return ARTICLE_LIST_KEY_PREFIX + articleId;
    }

    public ArticlePageIndex getLatestPageIndex(long version, int page, int size) {
        return redisStore.getObject(
                latestPageKey(version, page, size),
                ArticlePageIndex.class
        );
    }

    /**
     * 返回期望版本表示发布成功，返回其他正数表示应切换版本，返回 -1 表示锁已失效。
     */
    public long publishLatestPageIndex(long version,
                                       int page,
                                       int size,
                                       ArticlePageIndex pageIndex,
                                       LockHandle lockHandle) {
        Long result = redisStore.executeLongScript(
                PUBLISH_PAGE_INDEX_SCRIPT,
                List.of(
                        LATEST_VERSION_KEY,
                        latestPageKey(version, page, size),
                        lockHandle.key()
                ),
                String.valueOf(version),
                lockHandle.token(),
                redisStore.serializeValue(pageIndex),
                String.valueOf(LATEST_PAGE_TTL_MILLIS)
        );
        return requireScriptResult(result, "发布最新文章分页索引");
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

    private boolean evictCache(String cacheKey, LockHandle lockHandle) {
        Long result = redisStore.executeLongScript(
                EVICT_CACHE_SCRIPT,
                List.of(lockHandle.key(), cacheKey),
                lockHandle.token()
        );
        return Long.valueOf(1L).equals(result);
    }

    private <T> CacheValue<T> readState(Object rawValue, Class<T> type) {
        if (rawValue == null) {
            return CacheValue.miss();
        }
        if (rawValue instanceof String value && NULL_CACHE_MARKER.equals(value)) {
            return CacheValue.negative();
        }
        return CacheValue.hit(redisStore.convertValue(rawValue, type));
    }

    private long negativeTtlMillis() {
        return NEGATIVE_TTL_MILLIS + ThreadLocalRandom.current().nextLong(
                NEGATIVE_TTL_JITTER_MILLIS + 1L
        );
    }

    private long requireScriptResult(Long result, String operation) {
        if (result == null) {
            throw new IllegalStateException(operation + "未返回执行结果");
        }
        return result;
    }

    public enum CacheState {
        HIT,
        NEGATIVE,
        MISS
    }

    /**
     * 显式表达正常命中、负缓存命中和真正未命中三种状态。
     */
    public record CacheValue<T>(CacheState state, T value) {

        public static <T> CacheValue<T> hit(T value) {
            return new CacheValue<>(CacheState.HIT, value);
        }

        public static <T> CacheValue<T> negative() {
            return new CacheValue<>(CacheState.NEGATIVE, null);
        }

        public static <T> CacheValue<T> miss() {
            return new CacheValue<>(CacheState.MISS, null);
        }
    }
}
