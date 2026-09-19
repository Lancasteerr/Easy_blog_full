package com.febrie.demo_bk.shared.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 基于 Redis token 的轻量分布式锁，避免请求误删其他线程后续获得的锁。
 */
@Service
@RequiredArgsConstructor
public class RedisDistributedLockService {

    private static final int MIN_RETRY_INTERVAL_MILLIS = 30;
    private static final int MAX_RETRY_INTERVAL_MILLIS = 80;

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT =
            new DefaultRedisScript<>("""
                    if redis.call('GET', KEYS[1]) == ARGV[1] then
                        return redis.call('DEL', KEYS[1])
                    end
                    return 0
                    """, Long.class);

    private final RedisStore redisStore;

    /**
     * 不等待地尝试获得锁，未获得时返回 {@code null}。
     */
    public LockHandle tryLock(String lockKey, Duration leaseTime) {
        return tryLock(lockKey, leaseTime, Duration.ZERO);
    }

    /**
     * 在给定等待时间内反复尝试获得锁，线程中断时立即停止等待。
     */
    public LockHandle tryLock(String lockKey,
                              Duration leaseTime,
                              Duration waitTime) {
        validateDuration(leaseTime, "锁租期");
        if (waitTime == null || waitTime.isNegative()) {
            throw new IllegalArgumentException("锁等待时间不能为负数");
        }

        long deadlineNanos = System.nanoTime() + waitTime.toNanos();
        do {
            String token = UUID.randomUUID().toString();
            if (redisStore.setStringIfAbsent(lockKey, token, leaseTime)) {
                return new LockHandle(lockKey, token);
            }

            if (System.nanoTime() >= deadlineNanos) {
                return null;
            }

            try {
                long remainingMillis = Math.max(
                        1L,
                        Duration.ofNanos(deadlineNanos - System.nanoTime()).toMillis()
                );
                long sleepMillis = Math.min(
                        remainingMillis,
                        ThreadLocalRandom.current().nextInt(
                                MIN_RETRY_INTERVAL_MILLIS,
                                MAX_RETRY_INTERVAL_MILLIS + 1
                        )
                );
                Thread.sleep(sleepMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return null;
            }
        } while (true);
    }

    /**
     * 只有 token 仍属于当前调用者时才删除锁。
     */
    public boolean unlock(LockHandle lockHandle) {
        if (lockHandle == null) {
            return false;
        }
        Long result = redisStore.executeLongScript(
                UNLOCK_SCRIPT,
                List.of(lockHandle.key()),
                lockHandle.token()
        );
        return Long.valueOf(1L).equals(result);
    }

    private void validateDuration(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + "必须大于0");
        }
    }

    /**
     * 保存锁 Key 和所有权 token，解锁时必须原样传回。
     */
    public record LockHandle(String key, String token) {
    }
}
