package com.febrie.demo_bk.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@TableName("article_view_stat")
@Getter
@Setter
public class ArticleViewStat {
    @TableId(type = IdType.AUTO)
    private int id;

    private int articleId;

    private String statDate;

    private int pv;
}
