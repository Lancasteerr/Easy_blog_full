package com.febrie.demo_bk.article.application.query;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.febrie.demo_bk.article.application.dto.ArticleListDTO;
import com.febrie.demo_bk.article.application.query.ArticleListHydrator.HydratedArticleList;
import com.febrie.demo_bk.article.infrastructure.cache.ArticlePageIndex;
import com.febrie.demo_bk.article.infrastructure.cache.LatestArticleIndexStore;
import com.febrie.demo_bk.article.infrastructure.persistence.ArticleMapper;
import com.febrie.demo_bk.shared.infrastructure.redis.RedisDistributedLockService;
import com.febrie.demo_bk.shared.infrastructure.redis.RedisDistributedLockService.LockHandle;
import com.febrie.demo_bk.shared.pagination.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LatestArticleListQueryTest {

    private ArticleMapper articleMapper;
    private LatestArticleIndexStore latestIndexStore;
    private ArticleListHydrator hydrator;
    private RedisDistributedLockService lockService;
    private LatestArticleListQuery latestQuery;

    @BeforeEach
    void setUp() {
        articleMapper = mock(ArticleMapper.class);
        latestIndexStore = mock(LatestArticleIndexStore.class);
        hydrator = mock(ArticleListHydrator.class);
        lockService = mock(RedisDistributedLockService.class);
        latestQuery = new LatestArticleListQuery(
                articleMapper,
                latestIndexStore,
                hydrator,
                lockService
        );
    }

    @Test
    void cacheHitShouldUseVersionedIndexAndHydrator() {
        ArticlePageIndex pageIndex = new ArticlePageIndex(List.of(6), 1L, 1, 10);
        ArticleListDTO cached = articleListDto(6, "最新文章");
        when(latestIndexStore.getVersion()).thenReturn(2L);
        when(latestIndexStore.getPageIndex(2L, 1, 10)).thenReturn(pageIndex);
        when(hydrator.hydrate(List.of(6), null)).thenReturn(hydrated(List.of(cached)));

        PageResult<ArticleListDTO> result = latestQuery.findPage(1, 10);

        assertThat(result.getContent()).singleElement()
                .extracting(ArticleListDTO::getArticleTitle)
                .isEqualTo("最新文章");
        verify(articleMapper, never()).selectArticleIdPage(any(), any());
    }

    @Test
    void indexPublishShouldSwitchToVersionReturnedByLua() {
        LockHandle indexLock = new LockHandle("index-lock", "token");
        ArticlePageIndex versionThree = new ArticlePageIndex(List.of(3), 1L, 1, 10);
        when(latestIndexStore.getVersion()).thenReturn(2L, 2L, 3L);
        when(latestIndexStore.getPageIndex(2L, 1, 10)).thenReturn(null);
        when(latestIndexStore.pageLockKey(2L, 1, 10)).thenReturn("index-lock");
        when(lockService.tryLock(
                eq("index-lock"),
                any(Duration.class),
                any(Duration.class)
        )).thenReturn(indexLock);
        when(lockService.unlock(indexLock)).thenReturn(true);
        Page<Integer> oldMysqlPage = new Page<>(1, 10);
        oldMysqlPage.setRecords(List.of(2));
        oldMysqlPage.setTotal(1L);
        when(articleMapper.selectArticleIdPage(any(), any())).thenReturn(oldMysqlPage);
        when(latestIndexStore.publishPageIndex(
                eq(2L),
                eq(1),
                eq(10),
                any(ArticlePageIndex.class),
                eq(indexLock)
        )).thenReturn(3L);
        when(latestIndexStore.getPageIndex(3L, 1, 10)).thenReturn(versionThree);
        when(hydrator.hydrate(List.of(3), null))
                .thenReturn(hydrated(List.of(articleListDto(3, "新版本文章"))));

        PageResult<ArticleListDTO> result = latestQuery.findPage(1, 10);

        assertThat(result.getContent()).singleElement()
                .extracting(ArticleListDTO::getArticleTitle)
                .isEqualTo("新版本文章");
        verify(latestIndexStore).publishPageIndex(
                eq(2L),
                eq(1),
                eq(10),
                any(ArticlePageIndex.class),
                eq(indexLock)
        );
    }

    @Test
    void secondInvalidVersionShouldFallbackToMysqlWithoutThirdRead() {
        ArticlePageIndex versionSeven = new ArticlePageIndex(List.of(7), 1L, 1, 10);
        ArticlePageIndex versionEight = new ArticlePageIndex(List.of(8), 1L, 1, 10);
        PageResult<ArticleListDTO> fallback = new PageResult<>(
                List.of(articleListDto(9, "数据库兜底")),
                1L,
                1
        );
        when(latestIndexStore.getVersion()).thenReturn(7L, 7L, 8L);
        when(latestIndexStore.getPageIndex(7L, 1, 10)).thenReturn(versionSeven);
        when(latestIndexStore.getPageIndex(8L, 1, 10)).thenReturn(versionEight);
        when(hydrator.hydrate(List.of(7), null)).thenReturn(missing(7));
        when(hydrator.hydrate(List.of(8), null)).thenReturn(missing(8));
        when(latestIndexStore.advanceVersion(7L)).thenReturn(8L);
        when(latestIndexStore.advanceVersion(8L)).thenReturn(9L);
        when(hydrator.loadLatestMysqlPage(1, 10)).thenReturn(fallback);

        PageResult<ArticleListDTO> result = latestQuery.findPage(1, 10);

        assertThat(result).isSameAs(fallback);
        verify(latestIndexStore).advanceVersion(7L);
        verify(latestIndexStore).advanceVersion(8L);
        verify(latestIndexStore, never()).getPageIndex(eq(9L), anyInt(), anyInt());
    }

    private HydratedArticleList hydrated(List<ArticleListDTO> dtos) {
        return new HydratedArticleList(dtos, Set.of(), Set.of(), Set.of());
    }

    private HydratedArticleList missing(int id) {
        return new HydratedArticleList(List.of(), Set.of(id), Set.of(), Set.of());
    }

    private ArticleListDTO articleListDto(int id, String title) {
        ArticleListDTO dto = new ArticleListDTO();
        dto.setId(id);
        dto.setArticleTitle(title);
        return dto;
    }
}
