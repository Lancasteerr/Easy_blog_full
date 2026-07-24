package com.febrie.demo_bk.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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

}
