package com.febrie.demo_bk.article.infrastructure.cache;

import com.febrie.demo_bk.shared.infrastructure.redis.RedisDistributedLockService.LockHandle;
import com.febrie.demo_bk.shared.infrastructure.redis.RedisStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 最新文章列表版本号和分页索引的 Redis 访问入口。
 */
@Component
@RequiredArgsConstructor
public class LatestArticleIndexStore {

    private static final String LATEST_VERSION_KEY = "blog:article:index:latest:version";
    private static final String LATEST_PAGE_KEY_PREFIX = "blog:article:index:latest:";
    private static final String LATEST_PAGE_LOCK_KEY_PREFIX =
            "blog:lock:article:index:latest:";
    private static final long LATEST_PAGE_TTL_MILLIS = TimeUnit.HOURS.toMillis(1L);

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

    private static final DefaultRedisScript<Long> INCREASE_VERSION_SCRIPT =
            new DefaultRedisScript<>("""
                    if not redis.call('GET', KEYS[1]) then
                        redis.call('SET', KEYS[1], 1)
                    end
                    return redis.call('INCR', KEYS[1])
                    """, Long.class);

    private final RedisStore redisStore;

    public long getVersion() {
        redisStore.setIfAbsent(LATEST_VERSION_KEY, 1L);
        Long version = redisStore.getObject(LATEST_VERSION_KEY, Long.class);
        return version == null ? 1L : version;
    }

    public long increaseVersion() {
        Long result = redisStore.executeLongScript(
                INCREASE_VERSION_SCRIPT,
                List.of(LATEST_VERSION_KEY)
        );
        return requireScriptResult(result, "推进最新文章列表版本");
    }

    public long advanceVersion(long sourceVersion) {
        Long result = redisStore.executeLongScript(
                ADVANCE_VERSION_SCRIPT,
                List.of(LATEST_VERSION_KEY),
                String.valueOf(sourceVersion)
        );
        return requireScriptResult(result, "条件推进最新文章列表版本");
    }

    public String pageKey(long version, int page, int size) {
        return LATEST_PAGE_KEY_PREFIX + version + ":" + page + ":" + size;
    }

    public String pageLockKey(long version, int page, int size) {
        return LATEST_PAGE_LOCK_KEY_PREFIX + version + ":" + page + ":" + size;
    }

    public ArticlePageIndex getPageIndex(long version, int page, int size) {
        return redisStore.getObject(pageKey(version, page, size), ArticlePageIndex.class);
    }

    /**
     * 返回期望版本表示发布成功，其他正数表示应切换版本，-1 表示锁已失效。
     */
    public long publishPageIndex(long version,
                                 int page,
                                 int size,
                                 ArticlePageIndex pageIndex,
                                 LockHandle lockHandle) {
        Long result = redisStore.executeLongScript(
                PUBLISH_PAGE_INDEX_SCRIPT,
                List.of(
                        LATEST_VERSION_KEY,
                        pageKey(version, page, size),
                        lockHandle.key()
                ),
                String.valueOf(version),
                lockHandle.token(),
                redisStore.serializeValue(pageIndex),
                String.valueOf(LATEST_PAGE_TTL_MILLIS)
        );
        return requireScriptResult(result, "发布最新文章分页索引");
    }

    private long requireScriptResult(Long result, String operation) {
        if (result == null) {
            throw new IllegalStateException(operation + "未返回执行结果");
        }
        return result;
    }
}
