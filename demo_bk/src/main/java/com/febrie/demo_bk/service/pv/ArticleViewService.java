package com.febrie.demo_bk.service.pv;

import com.febrie.demo_bk.service.ArticleIndexRedisService;
import lombok.extern.slf4j.Slf4j;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Service
@AllArgsConstructor
public class ArticleViewService
        implements ArticleViewServiceImpl{

    private final ArticleIndexRedisService articleIndexRedisService;

    private static final String VIEW_KEY_PREFIX = "blog:article:view";

    /**
     * 明确统计时区，不依赖服务器时区
     * 根据业务所在地调整
     */
    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Shanghai");

    @Override
    public Long recordView(Long articleId) {
        return recordView(articleId, 0L);
    }

    @Override
    public Long recordView(Long articleId, Long persistedViewCount) {

        if (articleId == null || articleId <= 0) {
            return null;
        }

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        String redisKey = buildViewKey(today);

        try {
            // Lua 在 Redis 内原子写入当天 PV 与累计浏览量排行榜。
            return articleIndexRedisService.incrementView(
                    articleId.intValue(),
                    redisKey,
                    persistedViewCount == null ? 0L : persistedViewCount
            );
        } catch (RuntimeException exception) {
            // Redis 故障不能影响公开文章的正常阅读，后续校准任务会恢复排行榜成员。
            log.warn("Record article view failed, articleId={}", articleId, exception);
            return null;
        }
    }

    public static String buildViewKey(LocalDate date) {
        return VIEW_KEY_PREFIX + date;
    }

}
