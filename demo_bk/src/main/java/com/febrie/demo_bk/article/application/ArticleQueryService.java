package com.febrie.demo_bk.article.application;

import com.febrie.demo_bk.article.application.dto.ArticleDTO;
import com.febrie.demo_bk.article.application.dto.ArticleListDTO;
import com.febrie.demo_bk.article.application.query.ArticleDetailQuery;
import com.febrie.demo_bk.article.application.query.ArticleViewRankQuery;
import com.febrie.demo_bk.article.application.query.LatestArticleListQuery;
import com.febrie.demo_bk.shared.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 文章读取用例的稳定门面，负责参数校验和查询策略分派。
 */
@Service
@RequiredArgsConstructor
public class ArticleQueryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ArticleDetailQuery articleDetailQuery;
    private final LatestArticleListQuery latestArticleListQuery;
    private final ArticleViewRankQuery articleViewRankQuery;

    /**
     * 查询文章详情，详情缓存与回源策略由专用查询对象承担。
     */
    public ArticleDTO findById(int articleId) {
        if (articleId <= 0) {
            throw new IllegalArgumentException("文章ID不合法");
        }
        return articleDetailQuery.findById(articleId);
    }

    /**
     * 查询文章分页，并根据兼容的排序参数选择最新列表或浏览量榜。
     */
    public PageResult<ArticleListDTO> findPage(int page, int size, String sort) {
        validatePageParams(page, size);
        return "viewCountDesc".equals(sort)
                ? articleViewRankQuery.findPage(page, size)
                : latestArticleListQuery.findPage(page, size);
    }

    private void validatePageParams(int page, int size) {
        if (page < 1) {
            throw new IllegalArgumentException("页码不能小于1");
        }
        if (size < 1) {
            throw new IllegalArgumentException("每页数量不能小于1");
        }
        if (size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("每页数量不能超过" + MAX_PAGE_SIZE);
        }
    }
}
