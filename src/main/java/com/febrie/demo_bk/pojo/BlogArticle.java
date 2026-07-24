package com.febrie.demo_bk.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.febrie.demo_bk.dto.ArticleDTO;
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

        return blogArticle;
    }
}
