package com.febrie.demo_bk.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.febrie.demo_bk.pojo.BlogArticle;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BlogArticleDAO extends BaseMapper<BlogArticle> {
    BlogArticle selectById(int id);
}
