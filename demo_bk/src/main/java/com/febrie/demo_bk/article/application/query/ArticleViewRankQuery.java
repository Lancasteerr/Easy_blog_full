package com.febrie.demo_bk.article.application.query;

import com.febrie.demo_bk.article.application.dto.ArticleListDTO;
import com.febrie.demo_bk.article.application.query.ArticleListHydrator.HydratedArticleList;
import com.febrie.demo_bk.article.infrastructure.cache.ArticleViewStore;
import com.febrie.demo_bk.shared.error.ResourceNotFoundException;
import com.febrie.demo_bk.shared.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 文章浏览量排行榜查询，负责排行榜读取、失效成员修复和 MySQL 快照降级。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleViewRankQuery {

    private static final int MAX_RANK_HYDRATE_ATTEMPTS = 2;

    private final ArticleViewStore articleViewStore;
    private final ArticleListHydrator articleListHydrator;

    /**
     * 没有引入额外监控依赖时，计数随结构化告警日志一同输出。
     */
    private final AtomicLong rankRepairExhaustedCount = new AtomicLong();

    /**
     * 查询浏览量排行榜；最多重读一次，第二轮仍失效时删除成员并返回短页。
     */
    public PageResult<ArticleListDTO> findPage(int page, int size) {
        try {
            if (!articleViewStore.hasRankIndex()) {
                return articleListHydrator.loadViewRankMysqlPage(page, size);
            }
        } catch (RuntimeException exception) {
            log.warn("检查文章排行榜失败，回退MySQL快照", exception);
            return articleListHydrator.loadViewRankMysqlPage(page, size);
        }

        for (int attempt = 0; attempt < MAX_RANK_HYDRATE_ATTEMPTS; attempt++) {
            ArticleViewStore.RankPage rankPage;
            try {
                rankPage = articleViewStore.getRankPage(page, size);
            } catch (RuntimeException exception) {
                log.warn("读取文章排行榜失败，回退MySQL快照", exception);
                return articleListHydrator.loadViewRankMysqlPage(page, size);
            }

            validatePageBounds(rankPage.totalElements(), page, size);
            HydratedArticleList hydrated = articleListHydrator.hydrate(
                    rankPage.ids(),
                    rankPage.scores()
            );

            if (!hydrated.confirmedMissingIds().isEmpty()) {
                try {
                    articleViewStore.removeRankMembers(hydrated.confirmedMissingIds());
                } catch (RuntimeException exception) {
                    log.warn("清理排行榜失效成员失败，回退MySQL快照", exception);
                    return articleListHydrator.loadViewRankMysqlPage(page, size);
                }
            }

            if (!hydrated.unresolvedIds().isEmpty()) {
                log.warn(
                        "排行榜存在未解决ID，回退MySQL快照，page={}，size={}，ids={}",
                        page,
                        size,
                        hydrated.unresolvedIds()
                );
                return articleListHydrator.loadViewRankMysqlPage(page, size);
            }

            if (hydrated.confirmedMissingIds().isEmpty()) {
                return new PageResult<>(hydrated.dtos(), rankPage.totalElements(), page);
            }

            if (attempt == MAX_RANK_HYDRATE_ATTEMPTS - 1) {
                long totalElements;
                try {
                    totalElements = articleViewStore.getRankSize();
                } catch (RuntimeException exception) {
                    log.warn("读取清理后的排行榜总数失败，回退MySQL快照", exception);
                    return articleListHydrator.loadViewRankMysqlPage(page, size);
                }
                validatePageBounds(totalElements, page, size);
                long count = rankRepairExhaustedCount.incrementAndGet();
                log.warn(
                        "metric=rank_repair_exhausted count={} page={} size={} ids={}",
                        count,
                        page,
                        size,
                        hydrated.confirmedMissingIds()
                );
                return new PageResult<>(hydrated.dtos(), totalElements, page);
            }
        }

        throw new IllegalStateException("排行榜修复流程未产生结果");
    }

    private void validatePageBounds(long totalElements, int page, int size) {
        if (totalElements <= 0) {
            return;
        }
        long maxPage = (totalElements + size - 1) / size;
        if (page > maxPage) {
            throw new ResourceNotFoundException("文章列表页不存在");
        }
    }
}
