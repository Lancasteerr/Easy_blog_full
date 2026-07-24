package com.febrie.demo_bk.service.pv;

public interface ArticleViewServiceImpl {

    /**
     * 记录一次文章浏览
     */
    void recordView(Long articleId);
}
