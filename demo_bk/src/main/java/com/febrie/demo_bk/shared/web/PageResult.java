package com.febrie.demo_bk.shared.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PageResult<T> {
    private List<T> content;

    private long totalElements;

    private int number;

    /**
     * 必须有无参构造，Jackson反序列化时会有类似语句：PageResult = new PageResult();
     */
    public PageResult(){}

    public PageResult(List<T> content,
                      long totalElements,
                      int number) {
        this.content = content;
        this.totalElements = totalElements;
        this.number = number;
    }

    /**
     * 将 MyBatis Plus 分页结果转换成稳定的接口分页结构。
     */
    public static <T> PageResult<T> from(Page<T> page){
        return new PageResult<>(page.getRecords(),
                page.getTotal(),
                (int) page.getCurrent());
    }
}
