package com.febrie.demo_bk.service.pv;

import com.febrie.demo_bk.service.ArticleIndexRedisService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArticleViewServiceTest {

    @Test
    void recordViewShouldReturnRealtimeTotal() {
        ArticleIndexRedisService indexService = mock(ArticleIndexRedisService.class);
        when(indexService.incrementView(org.mockito.ArgumentMatchers.eq(1), anyString(), anyLong()))
                .thenReturn(12L);

        ArticleViewService service = new ArticleViewService(indexService);

        assertThat(service.recordView(1L)).isEqualTo(12L);
    }

    @Test
    void redisFailureShouldNotBlockArticleReading() {
        ArticleIndexRedisService indexService = mock(ArticleIndexRedisService.class);
        when(indexService.incrementView(org.mockito.ArgumentMatchers.eq(1), anyString(), anyLong()))
                .thenThrow(new IllegalStateException("redis unavailable"));

        ArticleViewService service = new ArticleViewService(indexService);

        // Redis 故障时返回 null，由详情服务保留数据库快照并正常响应。
        assertThat(service.recordView(1L)).isNull();
    }
}
