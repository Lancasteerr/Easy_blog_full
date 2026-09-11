package com.febrie.demo_bk.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleIndexRedisServiceTest {

    @Test
    void memberShouldKeepNumericOrderWhenRedisComparesTiesLexicographically() {
        // 统一补零后，Redis 的反向字典序即可表达数值 ID 倒序。
        assertThat(ArticleIndexRedisService.member(9)).isEqualTo("0000000009");
        assertThat(ArticleIndexRedisService.member(10)).isEqualTo("0000000010");
        assertThat(ArticleIndexRedisService.member(10).compareTo(ArticleIndexRedisService.member(9)))
                .isGreaterThan(0);
        assertThat(ArticleIndexRedisService.articleId("0000000010")).isEqualTo(10);
    }
}
