package com.febrie.demo_bk.shared.infrastructure.redis;

import com.febrie.demo_bk.shared.infrastructure.redis.RedisDistributedLockService.LockHandle;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisDistributedLockServiceTest {

    private final RedisStore redisStore = mock(RedisStore.class);
    private final RedisDistributedLockService lockService =
            new RedisDistributedLockService(redisStore);

    @Test
    void acquiredLockShouldContainUniqueOwnershipToken() {
        when(redisStore.setStringIfAbsent(
                anyString(),
                anyString(),
                any(Duration.class)
        )).thenReturn(true);

        LockHandle lockHandle = lockService.tryLock(
                "blog:lock:test",
                Duration.ofSeconds(10L)
        );

        assertThat(lockHandle).isNotNull();
        assertThat(lockHandle.key()).isEqualTo("blog:lock:test");
        assertThat(lockHandle.token()).isNotBlank();
    }

    @Test
    void unlockShouldFailWhenTokenNoLongerOwnsLock() {
        when(redisStore.executeLongScript(any(), anyList(), any(String[].class)))
                .thenReturn(0L);

        boolean unlocked = lockService.unlock(new LockHandle("lock", "old-token"));

        assertThat(unlocked).isFalse();
    }
}
