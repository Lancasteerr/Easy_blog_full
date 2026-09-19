package com.febrie.demo_bk.article.application;

import com.febrie.demo_bk.article.application.dto.ArticleDTO;
import com.febrie.demo_bk.article.application.dto.ArticleListDTO;
import com.febrie.demo_bk.article.application.query.ArticleDetailQuery;
import com.febrie.demo_bk.article.application.query.ArticleViewRankQuery;
import com.febrie.demo_bk.article.application.query.LatestArticleListQuery;
import com.febrie.demo_bk.shared.pagination.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ArticleQueryServiceTest {

    private ArticleDetailQuery detailQuery;
    private LatestArticleListQuery latestQuery;
    private ArticleViewRankQuery rankQuery;
    private ArticleQueryService queryService;

    @BeforeEach
    void setUp() {
        detailQuery = mock(ArticleDetailQuery.class);
        latestQuery = mock(LatestArticleListQuery.class);
        rankQuery = mock(ArticleViewRankQuery.class);
        queryService = new ArticleQueryService(detailQuery, latestQuery, rankQuery);
    }

    @Test
    void findByIdShouldDelegateAfterValidation() {
        ArticleDTO articleDTO = new ArticleDTO();
        articleDTO.setId(3);
        when(detailQuery.findById(3)).thenReturn(articleDTO);

        assertThat(queryService.findById(3)).isSameAs(articleDTO);
        verify(detailQuery).findById(3);
    }

    @Test
    void defaultSortShouldUseLatestQuery() {
        PageResult<ArticleListDTO> expected = new PageResult<>(List.of(), 0L, 1);
        when(latestQuery.findPage(1, 10)).thenReturn(expected);

        assertThat(queryService.findPage(1, 10, null)).isSameAs(expected);
        verify(latestQuery).findPage(1, 10);
        verifyNoInteractions(rankQuery);
    }

    @Test
    void viewCountSortShouldUseRankQuery() {
        PageResult<ArticleListDTO> expected = new PageResult<>(List.of(), 0L, 1);
        when(rankQuery.findPage(1, 10)).thenReturn(expected);

        assertThat(queryService.findPage(1, 10, "viewCountDesc")).isSameAs(expected);
        verify(rankQuery).findPage(1, 10);
        verifyNoInteractions(latestQuery);
    }

    @Test
    void invalidParametersShouldBeRejectedBeforeDelegation() {
        assertThatThrownBy(() -> queryService.findById(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queryService.findPage(0, 10, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queryService.findPage(1, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queryService.findPage(1, 51, null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(detailQuery, latestQuery, rankQuery);
    }
}
