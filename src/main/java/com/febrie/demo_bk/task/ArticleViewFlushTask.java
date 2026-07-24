package com.febrie.demo_bk.task;

import com.febrie.demo_bk.dao.ArticleViewStatDAO;
import com.febrie.demo_bk.dao.BlogArticleDAO;
import com.febrie.demo_bk.service.BlogArticleService;
import com.febrie.demo_bk.service.RedisService;
import com.febrie.demo_bk.service.pv.ArticleViewService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 将Redis中的浏览量刷到Mysql
 * <p>
 * 并删除浏览量更新的文章的详细缓存
 * <p>
 * 并自增版本号 废除所有文章列表
 */
@Slf4j
@Component
@AllArgsConstructor
public class ArticleViewFlushTask {

    private final RedisService redisService;
    private final ArticleViewStatDAO articleViewStatDAO;
    private final BlogArticleDAO blogArticleDAO;
    private final BlogArticleService blogArticleService;

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Shanghai");

    //5min
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    @Transactional(rollbackFor = Exception.class)
    public void flushViewStats() {
        //刷新浏览量的文章id
        Set<Integer> changedArticleIds = new HashSet<>();

        flushDate(LocalDate.now(BUSINESS_ZONE).minusDays(1), changedArticleIds);
        flushDate(LocalDate.now(BUSINESS_ZONE), changedArticleIds);

        if (!changedArticleIds.isEmpty()) {
            changedArticleIds.forEach(blogArticleDAO::refreshTotalViewCount);
            blogArticleService.invalidateViewCountCache(changedArticleIds);
        }
    }

    private void flushDate(LocalDate statDate, Set<Integer> changedArticleIds) {
        String redisKey = ArticleViewService.buildViewKey(statDate);
        Map<Object, Object> viewCounts = redisService.getHashEntries(redisKey);
        if (viewCounts == null || viewCounts.isEmpty()) {
            return;
        }

        viewCounts.forEach((articleIdValue, pvCountValue) -> {
            Integer articleId = parseInteger(articleIdValue);
            Long pvCount = parseLong(pvCountValue);

            if (articleId == null || pvCount == null || pvCount < 0) {
                log.warn("Skip invalid article view stat, redisKey={}, articleId={}, pvCount={}",
                        redisKey, articleIdValue, pvCountValue);
                return;
            }

            articleViewStatDAO.updateViewCount(
                    articleId,
                    statDate.toString(),
                    pvCount
            );
            changedArticleIds.add(articleId);
        });
    }

    private Integer parseInteger(Object value) {
        try {
            return value == null ? null : Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(Object value) {
        try {
            return value == null ? null : Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
