package com.febrie.demo_bk.article.application.query;

import com.febrie.demo_bk.article.application.dto.ArticleListDTO;
import com.febrie.demo_bk.article.infrastructure.cache.ArticleListCacheStore;
import com.febrie.demo_bk.article.infrastructure.cache.ArticleViewStore;
import com.febrie.demo_bk.article.infrastructure.cache.CacheValue;
import com.febrie.demo_bk.article.infrastructure.persistence.ArticleMapper;
import com.febrie.demo_bk.shared.infrastructure.redis.RedisDistributedLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleListHydratorTest {

    private ArticleMapper articleMapper;
    private ArticleListCacheStore listCacheStore;
    private ArticleViewStore viewStore;
    private RedisDistributedLockService lockService;
    private ArticleListHydrator hydrator;

    @BeforeEach
    void setUp() {
        articleMapper = mock(ArticleMapper.class);
        listCacheStore = mock(ArticleListCacheStore.class);
        viewStore = mock(ArticleViewStore.class);
        lockService = mock(RedisDistributedLockService.class);
        hydrator = new ArticleListHydrator(
                articleMapper,
                listCacheStore,
                viewStore,
                lockService
        );
    }

    @Test
    void cacheHitShouldReturnCopyWithRealtimeViewCount() {
        ArticleListDTO cached = articleListDto(1, "缓存文章", 7L);
        when(listCacheStore.getStates(List.of(1)))
                .thenReturn(Map.of(1, CacheValue.hit(cached)));
        when(viewStore.getViewScores(List.of(1))).thenReturn(List.of(8L));

        ArticleListHydrator.HydratedArticleList result = hydrator.hydrate(
                List.of(1),
                null
        );

        assertThat(result.dtos()).singleElement().satisfies(dto -> {
            assertThat(dto).isNotSameAs(cached);
            assertThat(dto.getArticleTitle()).isEqualTo("缓存文章");
            assertThat(dto.getViewCount()).isEqualTo(8L);
        });
        verify(articleMapper, never()).selectArticleListByIds(any());
    }

    @Test
    void redisFailureShouldUseUnlockedBatchMysqlFallback() {
        ArticleListDTO databaseDto = articleListDto(2, "数据库文章", 4L);
        when(listCacheStore.getStates(List.of(2)))
                .thenThrow(new IllegalStateException("redis unavailable"));
        when(articleMapper.selectArticleListByIds(Set.of(2)))
                .thenReturn(List.of(databaseDto));
        when(viewStore.getViewScores(List.of(2))).thenReturn(List.of(5L));

        ArticleListHydrator.HydratedArticleList result = hydrator.hydrate(
                List.of(2),
                null
        );

        assertThat(result.dtos()).singleElement()
                .extracting(ArticleListDTO::getViewCount)
                .isEqualTo(5L);
        assertThat(result.unresolvedIds()).isEmpty();
        verify(lockService, never()).tryLock(any(), any(Duration.class));
    }

    @Test
    void confirmedMysqlMissShouldPublishNegativeCache() {
        RedisDistributedLockService.LockHandle lockHandle =
                new RedisDistributedLockService.LockHandle("list-lock", "token");
        when(listCacheStore.getStates(List.of(3)))
                .thenReturn(Map.of(3, CacheValue.miss()));
        when(listCacheStore.lockKey(3)).thenReturn("list-lock");
        when(lockService.tryLock(eq("list-lock"), any(Duration.class)))
                .thenReturn(lockHandle);
        when(articleMapper.selectArticleListByIds(Set.of(3))).thenReturn(List.of());
        when(listCacheStore.publishMissing(3, lockHandle)).thenReturn(true);
        when(lockService.unlock(lockHandle)).thenReturn(true);
        when(viewStore.getViewScores(List.of(3))).thenReturn(List.of(0L));

        ArticleListHydrator.HydratedArticleList result = hydrator.hydrate(
                List.of(3),
                null
        );

        assertThat(result.dtos()).isEmpty();
        assertThat(result.confirmedMissingIds()).containsExactly(3);
        verify(listCacheStore).publishMissing(3, lockHandle);
    }

    @Test
    void contendedLockShouldConsumeStatePublishedByOwner() {
        ArticleListDTO cached = articleListDto(4, "竞争后命中", 10L);
        when(listCacheStore.getStates(List.of(4)))
                .thenReturn(Map.of(4, CacheValue.miss()))
                .thenReturn(Map.of(4, CacheValue.hit(cached)));
        when(listCacheStore.lockKey(4)).thenReturn("list-lock");
        when(lockService.tryLock(eq("list-lock"), any(Duration.class)))
                .thenReturn(null);
        when(viewStore.getViewScores(List.of(4))).thenReturn(List.of(11L));

        ArticleListHydrator.HydratedArticleList result = hydrator.hydrate(
                List.of(4),
                null
        );

        assertThat(result.dtos()).singleElement()
                .extracting(ArticleListDTO::getViewCount)
                .isEqualTo(11L);
        verify(articleMapper, never()).selectArticleListByIds(any());
    }

    private ArticleListDTO articleListDto(int id, String title, long viewCount) {
        ArticleListDTO dto = new ArticleListDTO();
        dto.setId(id);
        dto.setArticleTitle(title);
        dto.setViewCount(viewCount);
        return dto;
    }
}
