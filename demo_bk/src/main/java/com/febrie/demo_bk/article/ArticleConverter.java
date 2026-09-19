package com.febrie.demo_bk.article;

import com.febrie.demo_bk.article.internal.BlogArticle;

/**
 * 文章应用层 DTO 与持久化对象之间的转换器。
 *
 * <p>保持包内可见，避免将内部持久化模型转换能力暴露为模块公共契约。</p>
 */
final class ArticleConverter {

    private ArticleConverter() {
        // 无状态转换器不允许实例化。
    }

    /**
     * 将文章持久化对象转换为接口使用的 DTO。
     */
    static ArticleDTO toDto(BlogArticle article) {
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
    static BlogArticle toEntity(ArticleDTO articleDTO) {
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
