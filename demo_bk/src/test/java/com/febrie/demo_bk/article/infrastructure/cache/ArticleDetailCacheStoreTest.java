package com.febrie.demo_bk.article.infrastructure.cache;

import com.febrie.demo_bk.article.application.dto.ArticleDTO;
import com.febrie.demo_bk.article.infrastructure.cache.CacheValue.CacheState;
import com.febrie.demo_bk.shared.infrastructure.redis.RedisDistributedLockService.LockHandle;
import com.febrie.demo_bk.shared.infrastructure.redis.RedisStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleDetailCacheStoreTest {

    private final RedisStore redisStore = mock(RedisStore.class);
    private final ArticleDetailCacheStore cacheStore =
            new ArticleDetailCacheStore(redisStore);

    @Test
    void shouldRecognizeNegativeMarkerBeforeDtoConversion() {
        when(redisStore.getRawObject("blog:article:detail:3"))
                .thenReturn("__NULL__");

        CacheValue<ArticleDTO> result = cacheStore.getState(3);

        assertThat(result.state()).isEqualTo(CacheState.NEGATIVE);
        assertThat(result.value()).isNull();
    }

    @Test
    void publishShouldUseCompatibleDetailKeyAndLockToken() {
        ArticleDTO articleDTO = new ArticleDTO();
        articleDTO.setId(3);
        LockHandle lockHandle = new LockHandle("detail-lock", "token");
        when(redisStore.serializeValue(articleDTO)).thenReturn("serialized");
        when(redisStore.executeLongScript(any(), anyList(), any(String[].class)))
                .thenReturn(1L);

        assertThat(cacheStore.publish(3, articleDTO, lockHandle)).isTrue();
        assertThat(cacheStore.lockKey(3)).isEqualTo("blog:lock:article:detail:3");
        verify(redisStore).executeLongScript(
                any(),
                org.mockito.ArgumentMatchers.eq(List.of(
                        "detail-lock",
                        "blog:article:detail:3"
                )),
                org.mockito.ArgumentMatchers.eq("token"),
                org.mockito.ArgumentMatchers.eq("serialized"),
                org.mockito.ArgumentMatchers.eq("2592000000")
        );
    }
}
