package com.febrie.demo_bk.article.internal;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.febrie.demo_bk.article.ArticleDTO;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("blog_article")
@JsonIgnoreProperties({"handler", "hibernateLazyInitializer"})
@Data
public class BlogArticle {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String articleTitle;

    private String articleContentHtml;

    private String articleContentJson;

    private String articleAbstract;

    private LocalDateTime articleDate;

    private Long viewCount;

    // 删除封面时必须让 updateById 将 NULL 显式写入数据库。
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long articleCover;

    // 封面被删除时同步清空持久化的访问地址，避免遗留旧 URL。
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String coverObjectUrl;

    public static ArticleDTO toDTO(BlogArticle blogArticle) {
        if (blogArticle == null) return null;

        ArticleDTO articleDTO = new ArticleDTO();

        articleDTO.setId(blogArticle.getId());
        articleDTO.setArticleAbstract(blogArticle.getArticleAbstract());
        articleDTO.setArticleContentHtml(blogArticle.getArticleContentHtml());
        articleDTO.setArticleContentJson(blogArticle.getArticleContentJson());
        articleDTO.setArticleTitle(blogArticle.getArticleTitle());
        articleDTO.setArticleDate(blogArticle.getArticleDate());
        articleDTO.setViewCount(blogArticle.getViewCount());
        articleDTO.setArticleCover(blogArticle.articleCover);
        articleDTO.setCoverObjectUrl(blogArticle.getCoverObjectUrl());

        return articleDTO;
    }

    public static BlogArticle toPojo(ArticleDTO articleDTO) {
        if (articleDTO == null) return null;

        BlogArticle blogArticle = new BlogArticle();

        blogArticle.setArticleDate(articleDTO.getArticleDate());
        blogArticle.setArticleAbstract(articleDTO.getArticleAbstract());
        blogArticle.setId(articleDTO.getId());
        blogArticle.setArticleTitle(articleDTO.getArticleTitle());
        blogArticle.setArticleContentJson(articleDTO.getArticleContentJson());
        blogArticle.setArticleContentHtml(articleDTO.getArticleContentHtml());
        blogArticle.setViewCount(articleDTO.getViewCount());
        blogArticle.setArticleCover(articleDTO.getArticleCover());
        blogArticle.setCoverObjectUrl(articleDTO.getCoverObjectUrl());

        return blogArticle;
    }
}
