package com.febrie.demo_bk.article.application;

import com.febrie.demo_bk.article.infrastructure.cache.ArticleViewStore;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArticleViewServiceTest {

    @Test
    void recordViewShouldReturnRealtimeTotal() {
        ArticleViewStore viewStore = mock(ArticleViewStore.class);
        when(viewStore.incrementView(eq(1), any(LocalDate.class), anyLong()))
                .thenReturn(12L);

        ArticleViewService service = new ArticleViewService(viewStore);

        assertThat(service.recordView(1L)).isEqualTo(12L);
    }

    @Test
    void redisFailureShouldNotBlockArticleReading() {
        ArticleViewStore viewStore = mock(ArticleViewStore.class);
        when(viewStore.incrementView(eq(1), any(LocalDate.class), anyLong()))
                .thenThrow(new IllegalStateException("redis unavailable"));

        ArticleViewService service = new ArticleViewService(viewStore);

        // Redis 故障时返回 null，由详情服务保留数据库快照并正常响应。
        assertThat(service.recordView(1L)).isNull();
    }
}
