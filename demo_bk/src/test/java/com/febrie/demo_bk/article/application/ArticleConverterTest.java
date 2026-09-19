package com.febrie.demo_bk.article.application;

import com.febrie.demo_bk.article.application.dto.ArticleDTO;
import com.febrie.demo_bk.article.infrastructure.persistence.BlogArticle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁定文章 DTO 与持久化对象之间的完整字段映射。
 */
class ArticleConverterTest {

    private static final LocalDateTime ARTICLE_DATE =
            LocalDateTime.of(2026, 9, 19, 12, 30);

    @Test
    @DisplayName("持久化对象应完整转换为文章 DTO")
    void shouldConvertEntityToDto() {
        BlogArticle article = articleEntity();

        ArticleDTO result = ArticleConverter.toDto(article);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(7);
        assertThat(result.getArticleTitle()).isEqualTo("文章标题");
        assertThat(result.getArticleContentHtml()).isEqualTo("<p>正文</p>");
        assertThat(result.getArticleContentJson()).isEqualTo("{\"type\":\"doc\"}");
        assertThat(result.getArticleAbstract()).isEqualTo("文章摘要");
        assertThat(result.getArticleDate()).isEqualTo(ARTICLE_DATE);
        assertThat(result.getViewCount()).isEqualTo(31L);
        assertThat(result.getArticleCover()).isEqualTo(9L);
        assertThat(result.getCoverObjectUrl()).isEqualTo("/files/cover.webp");
    }

    @Test
    @DisplayName("文章 DTO 应完整转换为持久化对象")
    void shouldConvertDtoToEntity() {
        ArticleDTO articleDTO = articleDto();

        BlogArticle result = ArticleConverter.toEntity(articleDTO);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(7);
        assertThat(result.getArticleTitle()).isEqualTo("文章标题");
        assertThat(result.getArticleContentHtml()).isEqualTo("<p>正文</p>");
        assertThat(result.getArticleContentJson()).isEqualTo("{\"type\":\"doc\"}");
        assertThat(result.getArticleAbstract()).isEqualTo("文章摘要");
        assertThat(result.getArticleDate()).isEqualTo(ARTICLE_DATE);
        assertThat(result.getViewCount()).isEqualTo(31L);
        assertThat(result.getArticleCover()).isEqualTo(9L);
        assertThat(result.getCoverObjectUrl()).isEqualTo("/files/cover.webp");
    }

    @Test
    @DisplayName("空对象转换应继续返回空值")
    void shouldKeepNullConversionBehavior() {
        assertThat(ArticleConverter.toDto(null)).isNull();
        assertThat(ArticleConverter.toEntity(null)).isNull();
    }

    private BlogArticle articleEntity() {
        BlogArticle article = new BlogArticle();
        article.setId(7);
        article.setArticleTitle("文章标题");
        article.setArticleContentHtml("<p>正文</p>");
        article.setArticleContentJson("{\"type\":\"doc\"}");
        article.setArticleAbstract("文章摘要");
        article.setArticleDate(ARTICLE_DATE);
        article.setViewCount(31L);
        article.setArticleCover(9L);
        article.setCoverObjectUrl("/files/cover.webp");
        return article;
    }

    private ArticleDTO articleDto() {
        ArticleDTO articleDTO = new ArticleDTO();
        articleDTO.setId(7);
        articleDTO.setArticleTitle("文章标题");
        articleDTO.setArticleContentHtml("<p>正文</p>");
        articleDTO.setArticleContentJson("{\"type\":\"doc\"}");
        articleDTO.setArticleAbstract("文章摘要");
        articleDTO.setArticleDate(ARTICLE_DATE);
        articleDTO.setViewCount(31L);
        articleDTO.setArticleCover(9L);
        articleDTO.setCoverObjectUrl("/files/cover.webp");
        return articleDTO;
    }
}
