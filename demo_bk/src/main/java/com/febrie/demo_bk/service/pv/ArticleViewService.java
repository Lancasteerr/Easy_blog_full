package com.febrie.demo_bk.service.pv;

import com.febrie.demo_bk.service.RedisService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
public class ArticleViewService
        implements ArticleViewServiceImpl{

    private final RedisService redisService;

    private static final String VIEW_KEY_PREFIX = "blog:article:view";

    /**
     * 明确统计时区，不依赖服务器时区
     * 根据业务所在地调整
     */
    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Shanghai");

    @Override
    public void recordView(Long articleId) {

        if(articleId == null){
            return;
        }

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        String redisKey = buildViewKey(today);

        //redis浏览量增加
        redisService.hashStringValueIncrease(
                redisKey,
                articleId.toString(),
                1L
        );

        //保留两天，避免历史统计占用 Redis
        redisService.stringSetExpire(
                redisKey,
                2,
                TimeUnit.DAYS
        );

    }

    public static String buildViewKey(LocalDate date) {
        return VIEW_KEY_PREFIX + date;
    }

}
