package com.febrie.demo_bk.service.pv;

public interface ArticleViewServiceImpl {

    /**
     * 记录一次文章浏览
     */
    Long recordView(Long articleId);

    /**
     * 记录一次文章浏览，并在排行榜成员缺失时使用 MySQL 已持久化浏览量初始化分数。
     */
    Long recordView(Long articleId, Long persistedViewCount);
}
