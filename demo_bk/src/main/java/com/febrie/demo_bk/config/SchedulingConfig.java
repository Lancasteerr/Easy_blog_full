package com.febrie.demo_bk.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务开关。
 * 开发和生产默认启用，测试环境可关闭，避免测试启动时误写真实数据库。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "blog.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
