package com.febrie.demo_bk.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.febrie.demo_bk.pojo.ArticleViewStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ArticleViewStatDAO extends BaseMapper<ArticleViewStat> {

    /**
     * 将某篇文章某天的浏览量设置为 Redis 中的累计值
     * <p>
     * 注意这里是直接赋值 pv = #{id} 不是相加 不会重复计数
     */
    void updateViewCount(@Param("articleId") int articleId,
                         @Param("statDate") String statDate,
                         @Param("pvCount") Long pvCount
    );

    /**
     * 查询指定日期的全部文章浏览量
     */
    List<ArticleViewStat> selectByStatDate(
            @Param("statDate")LocalDate statDate
    );
}
