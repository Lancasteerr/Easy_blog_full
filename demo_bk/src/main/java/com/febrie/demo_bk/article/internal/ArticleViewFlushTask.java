package com.febrie.demo_bk.article.internal;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/**
 * 将Redis中的浏览量刷到Mysql
 * <p>
 * 每日 Hash 记录的是当天累计值，任务只把尚未落库的 delta 写入 MySQL。
 * 排行榜由浏览请求实时维护，刷库任务不得回写 ZSet 分数。
 */
@Slf4j
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "blog.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class ArticleViewFlushTask {

    private final ArticleViewStore articleViewStore;
    private final ArticleViewStatMapper articleViewStatMapper;
    private final ArticleMapper articleMapper;

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Shanghai");

    //30min
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    @Transactional(rollbackFor = Exception.class)
    public void flushViewStats() {
        flushDate(LocalDate.now(BUSINESS_ZONE).minusDays(1));
        flushDate(LocalDate.now(BUSINESS_ZONE));
    }

    private void flushDate(LocalDate statDate) {
        String redisKey = ArticleViewStore.dailyViewKey(statDate);
        Map<Object, Object> viewCounts = articleViewStore.getDailyViewCounts(statDate);
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

            flushArticleViewCount(articleId, statDate, pvCount);
        });
    }

    /**
     * Redis 与 MySQL 中的日统计都使用累计值，通过行锁读取旧值计算 delta，重复执行不会重复累计。
     */
    void flushArticleViewCount(int articleId,
                               LocalDate statDate,
                               long redisDailyPv) {
        Long persistedPv = articleViewStatMapper.selectPvForUpdate(articleId, statDate.toString());
        long oldPv = persistedPv == null ? 0L : persistedPv;
        long delta = redisDailyPv - oldPv;
        if (delta <= 0) {
            // Redis 计数异常回退时不减少 MySQL 已持久化统计与累计浏览量。
            return;
        }

        int updatedRows = articleMapper.incrementViewCount(articleId, delta);
        if (updatedRows == 0) {
            // 删除文章后的残留 Hash 字段无需继续落库，外键也会清理历史日统计。
            log.info("Skip deleted article view stat, articleId={}, statDate={}", articleId, statDate);
            return;
        }

        articleViewStatMapper.updateViewCount(articleId, statDate.toString(), redisDailyPv);
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
