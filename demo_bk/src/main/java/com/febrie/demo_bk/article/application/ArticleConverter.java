package com.febrie.demo_bk.article.application;

import com.febrie.demo_bk.article.application.dto.ArticleDTO;
import com.febrie.demo_bk.article.infrastructure.persistence.BlogArticle;

/**
 * 文章应用层 DTO 与持久化对象之间的转换器。
 *
 * <p>仅供文章应用层内部协作类复用，不属于面向其他业务模块的公共契约。</p>
 */
public final class ArticleConverter {

    private ArticleConverter() {
        // 无状态转换器不允许实例化。
    }

    /**
     * 将文章持久化对象转换为接口使用的 DTO。
     */
    public static ArticleDTO toDto(BlogArticle article) {
        if (article == null) {
            return null;
        }

        ArticleDTO articleDTO = new ArticleDTO();
        articleDTO.setId(article.getId());
        articleDTO.setArticleTitle(article.getArticleTitle());
        articleDTO.setArticleContentHtml(article.getArticleContentHtml());
        articleDTO.setArticleContentJson(article.getArticleContentJson());
        articleDTO.setArticleAbstract(article.getArticleAbstract());
        articleDTO.setArticleDate(article.getArticleDate());
        articleDTO.setViewCount(article.getViewCount());
        articleDTO.setArticleCover(article.getArticleCover());
        articleDTO.setCoverObjectUrl(article.getCoverObjectUrl());
        return articleDTO;
    }

    /**
     * 将文章 DTO 转换为 MyBatis 持久化对象。
     */
    public static BlogArticle toEntity(ArticleDTO articleDTO) {
        if (articleDTO == null) {
            return null;
        }

        BlogArticle article = new BlogArticle();
        article.setId(articleDTO.getId());
        article.setArticleTitle(articleDTO.getArticleTitle());
        article.setArticleContentHtml(articleDTO.getArticleContentHtml());
        article.setArticleContentJson(articleDTO.getArticleContentJson());
        article.setArticleAbstract(articleDTO.getArticleAbstract());
        article.setArticleDate(articleDTO.getArticleDate());
        article.setViewCount(articleDTO.getViewCount());
        article.setArticleCover(articleDTO.getArticleCover());
        article.setCoverObjectUrl(articleDTO.getCoverObjectUrl());
        return article;
    }
}
