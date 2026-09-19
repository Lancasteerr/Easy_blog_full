package com.febrie.demo_bk.article.infrastructure.cache;

import com.febrie.demo_bk.shared.infrastructure.redis.RedisDistributedLockService.LockHandle;
import com.febrie.demo_bk.shared.infrastructure.redis.RedisStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LatestArticleIndexStoreTest {

    private final RedisStore redisStore = mock(RedisStore.class);
    private final LatestArticleIndexStore indexStore = new LatestArticleIndexStore(redisStore);

    @Test
    void missingVersionShouldRetainCompatibleInitialValue() {
        when(redisStore.getObject("blog:article:index:latest:version", Long.class))
                .thenReturn(null);

        assertThat(indexStore.getVersion()).isEqualTo(1L);
        verify(redisStore).setIfAbsent("blog:article:index:latest:version", 1L);
    }

    @Test
    void pageIndexShouldUseCompatibleKeyAndOneHourTtl() {
        ArticlePageIndex pageIndex = new ArticlePageIndex(List.of(4), 1L, 1, 10);
        LockHandle lockHandle = new LockHandle(
                "blog:lock:article:index:latest:2:1:10",
                "token"
        );
        when(redisStore.serializeValue(pageIndex)).thenReturn("serialized");
        when(redisStore.executeLongScript(any(), any(), any(String[].class)))
                .thenReturn(2L);

        long result = indexStore.publishPageIndex(2L, 1, 10, pageIndex, lockHandle);

        assertThat(result).isEqualTo(2L);
        assertThat(indexStore.pageKey(2L, 1, 10))
                .isEqualTo("blog:article:index:latest:2:1:10");
        assertThat(indexStore.pageLockKey(2L, 1, 10))
                .isEqualTo("blog:lock:article:index:latest:2:1:10");
        verify(redisStore).executeLongScript(
                any(),
                org.mockito.ArgumentMatchers.eq(List.of(
                        "blog:article:index:latest:version",
                        "blog:article:index:latest:2:1:10",
                        "blog:lock:article:index:latest:2:1:10"
                )),
                org.mockito.ArgumentMatchers.eq("2"),
                org.mockito.ArgumentMatchers.eq("token"),
                org.mockito.ArgumentMatchers.eq("serialized"),
                org.mockito.ArgumentMatchers.eq("3600000")
        );
    }

    @Test
    void missingLuaResultShouldFailFast() {
        when(redisStore.executeLongScript(any(), any(), any(String[].class)))
                .thenReturn(null);

        assertThatThrownBy(() -> indexStore.advanceVersion(2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未返回执行结果");
    }
}
