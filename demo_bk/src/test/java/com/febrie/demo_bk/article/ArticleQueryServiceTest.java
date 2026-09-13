package com.febrie.demo_bk.article;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.febrie.demo_bk.article.internal.ArticleCacheStore;
import com.febrie.demo_bk.article.internal.ArticleMapper;
import com.febrie.demo_bk.article.internal.ArticleViewService;
import com.febrie.demo_bk.article.internal.ArticleViewStore;
import com.febrie.demo_bk.article.internal.BlogArticle;
import com.febrie.demo_bk.shared.web.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ArticleQueryServiceTest {

    private ArticleMapper articleMapper;
    private ArticleCacheStore cacheStore;
    private ArticleViewStore viewStore;
    private ArticleViewService viewService;
    private ArticleQueryService queryService;

    @BeforeEach
    void setUp() {
        articleMapper = mock(ArticleMapper.class);
        cacheStore = mock(ArticleCacheStore.class);
        viewStore = mock(ArticleViewStore.class);
        viewService = mock(ArticleViewService.class);
        queryService = new ArticleQueryService(
                articleMapper,
                cacheStore,
                viewStore,
                viewService
        );
    }

    @Test
    void detailShouldFallbackToMysqlWhenRedisFails() {
        BlogArticle article = new BlogArticle();
        article.setId(3);
        article.setArticleTitle("缓存降级");
        article.setViewCount(20L);
        when(cacheStore.getArticleDetail(3))
                .thenThrow(new IllegalStateException("redis unavailable"));
        when(articleMapper.selectById(3)).thenReturn(article);
        when(viewService.recordView(3L, 20L)).thenReturn(null);

        ArticleDTO result = queryService.findById(3);

        assertThat(result.getArticleTitle()).isEqualTo("缓存降级");
        assertThat(result.getViewCount()).isEqualTo(20L);
        verify(articleMapper).selectById(3);
    }

    @Test
    void detailCacheHitShouldAvoidMysqlAndUseRealtimeViewCount() {
        ArticleDTO cached = new ArticleDTO();
        cached.setId(4);
        cached.setArticleTitle("缓存命中");
        cached.setViewCount(30L);
        when(cacheStore.getArticleDetail(4)).thenReturn(cached);
        when(viewService.recordView(4L, 30L)).thenReturn(31L);

        ArticleDTO result = queryService.findById(4);

        assertThat(result.getArticleTitle()).isEqualTo("缓存命中");
        assertThat(result.getViewCount()).isEqualTo(31L);
        verifyNoInteractions(articleMapper);
    }

    @Test
    void latestListCacheMissShouldQueryMysqlAndCachePageIndex() {
        when(cacheStore.getLatestVersion()).thenReturn(2L);
        when(cacheStore.latestPageKey(2L, 1, 10)).thenReturn("latest-key");
        when(cacheStore.getLatestPageIndex("latest-key")).thenReturn(null);

        Page<Integer> mysqlPage = new Page<>(1, 10);
        mysqlPage.setRecords(List.of(6));
        mysqlPage.setTotal(1L);
        when(articleMapper.selectArticleIdPage(any(), any())).thenReturn(mysqlPage);
        when(cacheStore.getArticleListDtos(List.of(6)))
                .thenReturn(Collections.emptyMap());

        ArticleListDTO article = new ArticleListDTO();
        article.setId(6);
        article.setArticleTitle("最新文章");
        article.setViewCount(8L);
        when(articleMapper.selectArticleListByIds(List.of(6))).thenReturn(List.of(article));
        when(viewStore.getViewScores(List.of(6))).thenReturn(List.of(9L));

        PageResult<ArticleListDTO> result = queryService.findPage(1, 10, null);

        assertThat(result.getContent()).singleElement()
                .satisfies(dto -> {
                    assertThat(dto.getArticleTitle()).isEqualTo("最新文章");
                    assertThat(dto.getViewCount()).isEqualTo(9L);
                });
        verify(cacheStore).cacheLatestPageIndex(
                org.mockito.ArgumentMatchers.eq("latest-key"),
                any()
        );
    }

    @Test
    void rankingShouldFallbackToMysqlSnapshotWhenRedisFails() {
        when(viewStore.hasRankIndex())
                .thenThrow(new IllegalStateException("redis unavailable"));

        Page<Integer> mysqlPage = new Page<>(1, 10);
        mysqlPage.setRecords(List.of(5));
        mysqlPage.setTotal(1L);
        when(articleMapper.selectArticleIdPage(any(), any()))
                .thenReturn(mysqlPage);
        when(cacheStore.getArticleListDtos(List.of(5)))
                .thenReturn(Collections.emptyMap());

        ArticleListDTO article = new ArticleListDTO();
        article.setId(5);
        article.setArticleTitle("MySQL 快照");
        article.setArticleDate(LocalDateTime.of(2026, 9, 12, 10, 0));
        article.setViewCount(66L);
        when(articleMapper.selectArticleListByIds(List.of(5))).thenReturn(List.of(article));
        when(viewStore.getViewScores(List.of(5)))
                .thenThrow(new IllegalStateException("redis unavailable"));

        PageResult<ArticleListDTO> result =
                queryService.findPage(1, 10, "viewCountDesc");

        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getContent()).singleElement()
                .satisfies(dto -> {
                    assertThat(dto.getArticleTitle()).isEqualTo("MySQL 快照");
                    assertThat(dto.getViewCount()).isEqualTo(66L);
                });
    }
}
