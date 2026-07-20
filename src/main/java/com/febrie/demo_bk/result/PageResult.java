package com.febrie.demo_bk.result;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.febrie.demo_bk.pojo.BlogArticle;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PageResult {
    private List<BlogArticle> content;

    private long totalElements;

    private int number;

    /**
     * 必须有无参构造，Jackson反序列化时会有类似语句：PageResult = new PageResult();
     */
    public PageResult(){}

    public PageResult(List<BlogArticle> content, long totalElements, int number) {
        this.content = content;
        this.totalElements = totalElements;
        this.number = number;
    }

    public static PageResult from(Page<BlogArticle> BlogArticleS){
        return new PageResult(BlogArticleS.getRecords(),
                BlogArticleS.getTotal(),
                (int)BlogArticleS.getCurrent());
    }
}
