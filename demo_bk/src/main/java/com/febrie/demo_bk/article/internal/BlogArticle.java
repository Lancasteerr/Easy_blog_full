package com.febrie.demo_bk.article.internal;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("blog_article")
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
}
