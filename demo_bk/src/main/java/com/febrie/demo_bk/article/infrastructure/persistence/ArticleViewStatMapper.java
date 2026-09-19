package com.febrie.demo_bk.article.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ArticleViewStatMapper extends BaseMapper<ArticleViewStat> {

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
     * 锁定某篇文章当天已落库的浏览量，供增量刷库计算 delta。
     */
    Long selectPvForUpdate(@Param("articleId") int articleId,
                           @Param("statDate") String statDate);

    /**
     * 查询指定日期的全部文章浏览量
     */
    List<ArticleViewStat> selectByStatDate(
            @Param("statDate")LocalDate statDate
    );
}
