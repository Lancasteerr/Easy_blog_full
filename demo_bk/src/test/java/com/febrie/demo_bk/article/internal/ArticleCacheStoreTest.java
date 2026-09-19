package com.febrie.demo_bk.article.internal;

import com.febrie.demo_bk.article.ArticleDTO;
import com.febrie.demo_bk.article.ArticleListDTO;
import com.febrie.demo_bk.article.internal.ArticleCacheStore.CacheState;
import com.febrie.demo_bk.article.internal.ArticleCacheStore.CacheValue;
import com.febrie.demo_bk.shared.infrastructure.RedisDistributedLockService.LockHandle;
import com.febrie.demo_bk.shared.infrastructure.RedisStore;
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

class ArticleCacheStoreTest {

    private final RedisStore redisStore = mock(RedisStore.class);
    private final ArticleCacheStore cacheStore = new ArticleCacheStore(redisStore);

    @Test
    void detailShouldRecognizeNegativeMarkerBeforeDtoConversion() {
        when(redisStore.getRawObject("blog:article:detail:3"))
                .thenReturn("__NULL__");

        CacheValue<ArticleDTO> result = cacheStore.getArticleDetailState(3);

        assertThat(result.state()).isEqualTo(CacheState.NEGATIVE);
        assertThat(result.value()).isNull();
    }

    @Test
    void batchReadShouldDistinguishHitNegativeAndMiss() {
        ArticleListDTO dto = new ArticleListDTO();
        dto.setId(1);
        when(redisStore.getRawObjects(anyList()))
                .thenReturn(Arrays.asList(dto, "__NULL__", null));
        when(redisStore.convertValue(dto, ArticleListDTO.class)).thenReturn(dto);

        Map<Integer, CacheValue<ArticleListDTO>> result =
                cacheStore.getArticleListDtoStates(List.of(1, 2, 3));

        assertThat(result.get(1).state()).isEqualTo(CacheState.HIT);
        assertThat(result.get(2).state()).isEqualTo(CacheState.NEGATIVE);
        assertThat(result.get(3).state()).isEqualTo(CacheState.MISS);
    }

    @Test
    void missingValueShouldOnlyPublishWhenLuaAcceptsLockToken() {
        LockHandle lockHandle = new LockHandle("list-lock", "token");
        when(redisStore.serializeValue("__NULL__")).thenReturn("\"__NULL__\"");
        when(redisStore.executeLongScript(any(), anyList(), any(String[].class)))
                .thenReturn(1L);

        boolean published = cacheStore.publishMissingArticleListDto(9, lockHandle);

        assertThat(published).isTrue();
        verify(redisStore).serializeValue("__NULL__");
        verify(redisStore).executeLongScript(any(), anyList(), any(String[].class));
    }
}
