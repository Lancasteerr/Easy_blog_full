package com.febrie.demo_bk.dao;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.febrie.demo_bk.dto.ArticleListDTO;
import com.febrie.demo_bk.pojo.BlogArticle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BlogArticleDAO extends BaseMapper<BlogArticle> {
    BlogArticle selectById(int id);

    /**
     * 根据每日统计表重新计算文章总浏览量
     */
    int refreshTotalViewCount(
            @Param("articleId") int articleId
    );

    /**
     * 使用DTO分页
     */
    Page<ArticleListDTO> selectArticleListPage(
            Page<ArticleListDTO> page,
            @Param(Constants.WRAPPER)
            Wrapper<BlogArticle> queryWrapper
    );

}
