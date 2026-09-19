package com.febrie.demo_bk.article;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.febrie.demo_bk.article.internal.ArticleCacheStore;
import com.febrie.demo_bk.article.internal.ArticleCacheStore.CacheValue;
import com.febrie.demo_bk.article.internal.ArticleMapper;
import com.febrie.demo_bk.article.internal.ArticlePageIndex;
import com.febrie.demo_bk.article.internal.ArticleViewService;
import com.febrie.demo_bk.article.internal.ArticleViewStore;
import com.febrie.demo_bk.article.internal.BlogArticle;
import com.febrie.demo_bk.shared.infrastructure.RedisDistributedLockService;
import com.febrie.demo_bk.shared.infrastructure.RedisDistributedLockService.LockHandle;
import com.febrie.demo_bk.shared.web.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ArticleQueryServiceTest {

    private ArticleMapper articleMapper;
    private ArticleCacheStore cacheStore;
    private ArticleViewStore viewStore;
    private ArticleViewService viewService;
    private RedisDistributedLockService lockService;
    private ArticleQueryService queryService;

    @BeforeEach
    void setUp() {
        articleMapper = mock(ArticleMapper.class);
        cacheStore = mock(ArticleCacheStore.class);
        viewStore = mock(ArticleViewStore.class);
        viewService = mock(ArticleViewService.class);
        lockService = mock(RedisDistributedLockService.class);
        queryService = new ArticleQueryService(
                articleMapper,
                cacheStore,
                viewStore,
                viewService,
                lockService
        );
    }

    @Test
    void detailShouldFallbackToMysqlWhenRedisFails() {
        BlogArticle article = new BlogArticle();
        article.setId(3);
        article.setArticleTitle("缓存降级");
        article.setViewCount(20L);
        when(cacheStore.getArticleDetailState(3))
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
        when(cacheStore.getArticleDetailState(4)).thenReturn(CacheValue.hit(cached));
        when(viewService.recordView(4L, 30L)).thenReturn(31L);

        ArticleDTO result = queryService.findById(4);

        assertThat(result.getArticleTitle()).isEqualTo("缓存命中");
        assertThat(result.getViewCount()).isEqualTo(31L);
        verifyNoInteractions(articleMapper, lockService);
    }

    @Test
    void latestListCacheHitShouldUseCachedIndexAndDto() {
        ArticlePageIndex pageIndex = new ArticlePageIndex(List.of(6), 1L, 1, 10);
        ArticleListDTO cached = articleListDto(6, "最新文章", 8L);
        when(cacheStore.getLatestVersion()).thenReturn(2L);
        when(cacheStore.getLatestPageIndex(2L, 1, 10)).thenReturn(pageIndex);
        when(cacheStore.getArticleListDtoStates(List.of(6)))
                .thenReturn(Map.of(6, CacheValue.hit(cached)));
        when(viewStore.getViewScores(List.of(6))).thenReturn(List.of(9L));

        PageResult<ArticleListDTO> result = queryService.findPage(1, 10, null);

        assertThat(result.getContent()).singleElement()
                .satisfies(dto -> {
                    assertThat(dto.getArticleTitle()).isEqualTo("最新文章");
                    assertThat(dto.getViewCount()).isEqualTo(9L);
                });
        verify(articleMapper, never()).selectArticleIdPage(any(), any());
    }

    @Test
    void indexPublishShouldSwitchToNewVersionWhenLuaDetectsChange() {
        LockHandle indexLock = new LockHandle("index-lock", "token");
        ArticlePageIndex versionThree = new ArticlePageIndex(List.of(3), 1L, 1, 10);
        ArticleListDTO cached = articleListDto(3, "新版本文章", 5L);
        when(cacheStore.getLatestVersion()).thenReturn(2L, 2L, 3L);
        when(cacheStore.getLatestPageIndex(2L, 1, 10)).thenReturn(null);
        when(cacheStore.latestPageLockKey(2L, 1, 10)).thenReturn("index-lock");
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
        when(cacheStore.publishLatestPageIndex(
                eq(2L),
                eq(1),
                eq(10),
                any(ArticlePageIndex.class),
                eq(indexLock)
        )).thenReturn(3L);
        when(cacheStore.getLatestPageIndex(3L, 1, 10)).thenReturn(versionThree);
        when(cacheStore.getArticleListDtoStates(List.of(3)))
                .thenReturn(Map.of(3, CacheValue.hit(cached)));
        when(viewStore.getViewScores(List.of(3))).thenReturn(List.of(5L));

        PageResult<ArticleListDTO> result = queryService.findPage(1, 10, null);

        assertThat(result.getContent()).singleElement()
                .extracting(ArticleListDTO::getArticleTitle)
                .isEqualTo("新版本文章");
        verify(cacheStore).publishLatestPageIndex(
                eq(2L),
                eq(1),
                eq(10),
                any(ArticlePageIndex.class),
                eq(indexLock)
        );
    }

    @Test
    void rankingShouldFallbackToDirectMysqlDtoPageWhenRedisFails() {
        when(viewStore.hasRankIndex())
                .thenThrow(new IllegalStateException("redis unavailable"));
        Page<ArticleListDTO> mysqlPage = dtoPage(articleListDto(5, "MySQL快照", 66L));
        when(articleMapper.selectArticleListPage(any(), any())).thenReturn(mysqlPage);

        PageResult<ArticleListDTO> result =
                queryService.findPage(1, 10, "viewCountDesc");

        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getContent()).singleElement()
                .satisfies(dto -> {
                    assertThat(dto.getArticleTitle()).isEqualTo("MySQL快照");
                    assertThat(dto.getViewCount()).isEqualTo(66L);
                });
    }

    @Test
    void rankingShouldRemoveSecondRoundMissingIdsWithoutThirdRead() {
        ArticleViewStore.RankPage firstPage = new ArticleViewStore.RankPage(
                List.of(1),
                Map.of(1, 10L),
                2L
        );
        ArticleViewStore.RankPage secondPage = new ArticleViewStore.RankPage(
                List.of(2),
                Map.of(2, 9L),
                1L
        );
        when(viewStore.hasRankIndex()).thenReturn(true);
        when(viewStore.getRankPage(1, 10)).thenReturn(firstPage, secondPage);
        when(cacheStore.getArticleListDtoStates(List.of(1)))
                .thenReturn(Map.of(1, CacheValue.negative()));
        when(cacheStore.getArticleListDtoStates(List.of(2)))
                .thenReturn(Map.of(2, CacheValue.negative()));
        when(viewStore.getRankSize()).thenReturn(0L);

        PageResult<ArticleListDTO> result =
                queryService.findPage(1, 10, "viewCountDesc");

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        verify(viewStore, times(2)).getRankPage(1, 10);
        verify(viewStore).removeRankMembers(java.util.Set.of(1));
        verify(viewStore).removeRankMembers(java.util.Set.of(2));
    }

    @Test
    void secondInvalidLatestVersionShouldBeDiscardedAndFallbackToMysql() {
        ArticlePageIndex versionSeven = new ArticlePageIndex(List.of(7), 1L, 1, 10);
        ArticlePageIndex versionEight = new ArticlePageIndex(List.of(8), 1L, 1, 10);
        when(cacheStore.getLatestVersion()).thenReturn(7L, 7L, 8L);
        when(cacheStore.getLatestPageIndex(7L, 1, 10)).thenReturn(versionSeven);
        when(cacheStore.getLatestPageIndex(8L, 1, 10)).thenReturn(versionEight);
        when(cacheStore.getArticleListDtoStates(List.of(7)))
                .thenReturn(Map.of(7, CacheValue.negative()));
        when(cacheStore.getArticleListDtoStates(List.of(8)))
                .thenReturn(Map.of(8, CacheValue.negative()));
        when(cacheStore.advanceLatestVersion(7L)).thenReturn(8L);
        when(cacheStore.advanceLatestVersion(8L)).thenReturn(9L);
        when(articleMapper.selectArticleListPage(any(), any()))
                .thenReturn(dtoPage(articleListDto(9, "数据库兜底", 3L)));
        when(viewStore.getViewScores(List.of(9))).thenReturn(List.of(3L));

        PageResult<ArticleListDTO> result = queryService.findPage(1, 10, null);

        assertThat(result.getContent()).singleElement()
                .extracting(ArticleListDTO::getArticleTitle)
                .isEqualTo("数据库兜底");
        verify(cacheStore).advanceLatestVersion(7L);
        verify(cacheStore).advanceLatestVersion(8L);
        verify(cacheStore, never()).getLatestPageIndex(eq(9L), anyInt(), anyInt());
    }

    @Test
    void unresolvedLatestIdShouldFallbackWithoutAdvancingVersion() {
        ArticlePageIndex pageIndex = new ArticlePageIndex(List.of(4), 1L, 1, 10);
        LockHandle dtoLock = new LockHandle("list-lock", "token");
        when(cacheStore.getLatestVersion()).thenReturn(3L);
        when(cacheStore.getLatestPageIndex(3L, 1, 10)).thenReturn(pageIndex);
        when(cacheStore.getArticleListDtoStates(List.of(4)))
                .thenReturn(Map.of(4, CacheValue.miss()));
        when(cacheStore.articleListLockKey(4)).thenReturn("list-lock");
        when(lockService.tryLock(eq("list-lock"), any(Duration.class)))
                .thenReturn(dtoLock);
        when(lockService.unlock(dtoLock)).thenReturn(true);
        when(articleMapper.selectArticleListByIds(java.util.Set.of(4)))
                .thenThrow(new IllegalStateException("mysql unavailable"));
        when(articleMapper.selectArticleListPage(any(), any()))
                .thenReturn(dtoPage(articleListDto(4, "完整分页兜底", 2L)));
        when(viewStore.getViewScores(List.of(4))).thenReturn(List.of(2L));

        PageResult<ArticleListDTO> result = queryService.findPage(1, 10, null);

        assertThat(result.getContent()).singleElement()
                .extracting(ArticleListDTO::getArticleTitle)
                .isEqualTo("完整分页兜底");
        verify(cacheStore, never()).advanceLatestVersion(anyLong());
    }

    private ArticleListDTO articleListDto(int id, String title, long viewCount) {
        ArticleListDTO dto = new ArticleListDTO();
        dto.setId(id);
        dto.setArticleTitle(title);
        dto.setViewCount(viewCount);
        return dto;
    }

    private Page<ArticleListDTO> dtoPage(ArticleListDTO dto) {
        Page<ArticleListDTO> page = new Page<>(1, 10);
        page.setRecords(List.of(dto));
        page.setTotal(1L);
        return page;
    }
}
