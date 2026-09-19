package com.febrie.demo_bk.article.infrastructure.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleViewStoreTest {

    @Test
    void memberShouldKeepNumericOrderWhenRedisComparesTiesLexicographically() {
        // 统一补零后，Redis 的反向字典序即可表达数值 ID 倒序。
        assertThat(ArticleViewStore.member(9)).isEqualTo("0000000009");
        assertThat(ArticleViewStore.member(10)).isEqualTo("0000000010");
        assertThat(ArticleViewStore.member(10).compareTo(ArticleViewStore.member(9)))
                .isGreaterThan(0);
        assertThat(ArticleViewStore.articleId("0000000010")).isEqualTo(10);
    }
}
