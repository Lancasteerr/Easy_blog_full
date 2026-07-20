package com.febrie.demo_bk.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.febrie.demo_bk.pojo.ArticleViewStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ArticleViewStatDAO extends BaseMapper<ArticleViewStat> {
    //更新文章浏览量
    void updateViewCount(@Param("articleId") int articleId, @Param("statDate") String statDate,  @Param("pvCount") int pvCount);

    int getViewCountByArticleId(@Param("articleId") int articleId);
}
