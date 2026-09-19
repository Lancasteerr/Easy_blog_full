package com.febrie.demo_bk.article.internal;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.febrie.demo_bk.article.ArticleListDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ArticleMapper extends BaseMapper<BlogArticle> {
    /**
     * 只查询分页后的文章 ID，用于最新列表页面索引缓存。
     */
    Page<Integer> selectArticleIdPage(
            Page<Integer> page,
            @Param(Constants.WRAPPER)
            Wrapper<BlogArticle> queryWrapper
    );

    /**
     * 直接查询完整 DTO 分页，用于 Redis 状态无法确认时生成可靠的当前响应。
     */
    Page<ArticleListDTO> selectArticleListPage(
            Page<ArticleListDTO> page,
            @Param(Constants.WRAPPER)
            Wrapper<BlogArticle> queryWrapper
    );

    /**
     * 根据文章 ID 批量查询列表 DTO，返回顺序由调用方按原 ID 顺序恢复。
     */
    List<ArticleListDTO> selectArticleListByIds(
            @Param("ids") Collection<Integer> ids
    );

    /**
     * 增量累加已持久化总浏览量，避免刷库任务重复全表聚合。
     */
    int incrementViewCount(
            @Param("articleId") int articleId,
            @Param("delta") long delta
    );

}
