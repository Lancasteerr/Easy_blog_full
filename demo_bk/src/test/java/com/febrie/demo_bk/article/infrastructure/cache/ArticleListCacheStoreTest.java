package com.febrie.demo_bk.article.infrastructure.cache;

import com.febrie.demo_bk.article.application.dto.ArticleListDTO;
import com.febrie.demo_bk.article.infrastructure.cache.CacheValue.CacheState;
import com.febrie.demo_bk.shared.infrastructure.redis.RedisDistributedLockService.LockHandle;
import com.febrie.demo_bk.shared.infrastructure.redis.RedisStore;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleListCacheStoreTest {

    private final RedisStore redisStore = mock(RedisStore.class);
    private final ArticleListCacheStore cacheStore = new ArticleListCacheStore(redisStore);

    @Test
    void batchReadShouldDistinguishHitNegativeAndMiss() {
        ArticleListDTO dto = new ArticleListDTO();
        dto.setId(1);
        when(redisStore.getRawObjects(anyList()))
                .thenReturn(Arrays.asList(dto, "__NULL__", null));
        when(redisStore.convertValue(dto, ArticleListDTO.class)).thenReturn(dto);

        Map<Integer, CacheValue<ArticleListDTO>> result =
                cacheStore.getStates(List.of(1, 2, 3));

        assertThat(result.get(1).state()).isEqualTo(CacheState.HIT);
        assertThat(result.get(2).state()).isEqualTo(CacheState.NEGATIVE);
        assertThat(result.get(3).state()).isEqualTo(CacheState.MISS);
        verify(redisStore).getRawObjects(List.of(
                "blog:article:list:1",
                "blog:article:list:2",
                "blog:article:list:3"
        ));
    }

    @Test
    void missingValueShouldOnlyPublishWhenLuaAcceptsLockToken() {
        LockHandle lockHandle = new LockHandle("list-lock", "token");
        when(redisStore.serializeValue("__NULL__")).thenReturn("\"__NULL__\"");
        when(redisStore.executeLongScript(any(), anyList(), any(String[].class)))
                .thenReturn(1L);

        boolean published = cacheStore.publishMissing(9, lockHandle);

        assertThat(published).isTrue();
        assertThat(cacheStore.lockKey(9)).isEqualTo("blog:lock:article:list:9");
        verify(redisStore).serializeValue("__NULL__");
    }
}
