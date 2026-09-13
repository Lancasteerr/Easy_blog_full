package com.febrie.demo_bk.article.internal;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Service
@AllArgsConstructor
public class ArticleViewService
{

    private final ArticleViewStore articleViewStore;

    /**
     * 明确统计时区，不依赖服务器时区
     * 根据业务所在地调整
     */
    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Shanghai");

    public Long recordView(Long articleId) {
        return recordView(articleId, 0L);
    }

    public Long recordView(Long articleId, Long persistedViewCount) {

        if (articleId == null || articleId <= 0) {
            return null;
        }

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        try {
            // Lua 在 Redis 内原子写入当天 PV 与累计浏览量排行榜。
            return articleViewStore.incrementView(
                    articleId.intValue(),
                    today,
                    persistedViewCount == null ? 0L : persistedViewCount
            );
        } catch (RuntimeException exception) {
            // Redis 故障不能影响公开文章的正常阅读，后续校准任务会恢复排行榜成员。
            log.warn("Record article view failed, articleId={}", articleId, exception);
            return null;
        }
    }

}
