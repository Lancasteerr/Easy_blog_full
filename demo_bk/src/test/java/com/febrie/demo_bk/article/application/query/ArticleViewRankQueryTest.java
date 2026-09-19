package com.febrie.demo_bk.article.application.query;

import com.febrie.demo_bk.article.application.dto.ArticleListDTO;
import com.febrie.demo_bk.article.application.query.ArticleListHydrator.HydratedArticleList;
import com.febrie.demo_bk.article.infrastructure.cache.ArticleViewStore;
import com.febrie.demo_bk.shared.pagination.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleViewRankQueryTest {

    private ArticleViewStore viewStore;
    private ArticleListHydrator hydrator;
    private ArticleViewRankQuery rankQuery;

    @BeforeEach
    void setUp() {
        viewStore = mock(ArticleViewStore.class);
        hydrator = mock(ArticleListHydrator.class);
        rankQuery = new ArticleViewRankQuery(viewStore, hydrator);
    }

    @Test
    void redisFailureShouldFallbackToMysqlSnapshot() {
        PageResult<ArticleListDTO> fallback = new PageResult<>(
                List.of(articleListDto(5, "MySQL快照")),
                1L,
                1
        );
        when(viewStore.hasRankIndex())
                .thenThrow(new IllegalStateException("redis unavailable"));
        when(hydrator.loadViewRankMysqlPage(1, 10)).thenReturn(fallback);

        assertThat(rankQuery.findPage(1, 10)).isSameAs(fallback);
    }

    @Test
    void secondRoundMissingIdsShouldBeRemovedWithoutThirdRead() {
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
        when(hydrator.hydrate(List.of(1), firstPage.scores())).thenReturn(missing(1));
        when(hydrator.hydrate(List.of(2), secondPage.scores())).thenReturn(missing(2));
        when(viewStore.getRankSize()).thenReturn(0L);

        PageResult<ArticleListDTO> result = rankQuery.findPage(1, 10);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        verify(viewStore, times(2)).getRankPage(1, 10);
        verify(viewStore).removeRankMembers(Set.of(1));
        verify(viewStore).removeRankMembers(Set.of(2));
    }

    @Test
    void unresolvedMemberShouldFallbackToMysqlSnapshot() {
        ArticleViewStore.RankPage rankPage = new ArticleViewStore.RankPage(
                List.of(7),
                Map.of(7, 12L),
                1L
        );
        PageResult<ArticleListDTO> fallback = new PageResult<>(List.of(), 0L, 1);
        when(viewStore.hasRankIndex()).thenReturn(true);
        when(viewStore.getRankPage(1, 10)).thenReturn(rankPage);
        when(hydrator.hydrate(List.of(7), rankPage.scores())).thenReturn(
                new HydratedArticleList(List.of(), Set.of(), Set.of(), Set.of(7))
        );
        when(hydrator.loadViewRankMysqlPage(1, 10)).thenReturn(fallback);

        assertThat(rankQuery.findPage(1, 10)).isSameAs(fallback);
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
