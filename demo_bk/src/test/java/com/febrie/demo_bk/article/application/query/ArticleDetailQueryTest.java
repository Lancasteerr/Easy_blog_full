package com.febrie.demo_bk.article.application.query;

import com.febrie.demo_bk.article.application.ArticleViewService;
import com.febrie.demo_bk.article.application.dto.ArticleDTO;
import com.febrie.demo_bk.article.infrastructure.cache.ArticleDetailCacheStore;
import com.febrie.demo_bk.article.infrastructure.cache.CacheValue;
import com.febrie.demo_bk.article.infrastructure.persistence.ArticleMapper;
import com.febrie.demo_bk.article.infrastructure.persistence.BlogArticle;
import com.febrie.demo_bk.shared.error.ResourceNotFoundException;
import com.febrie.demo_bk.shared.infrastructure.redis.RedisDistributedLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ArticleDetailQueryTest {

    private ArticleMapper articleMapper;
    private ArticleDetailCacheStore detailCacheStore;
    private ArticleViewService viewService;
    private RedisDistributedLockService lockService;
    private ArticleDetailQuery detailQuery;

    @BeforeEach
    void setUp() {
        articleMapper = mock(ArticleMapper.class);
        detailCacheStore = mock(ArticleDetailCacheStore.class);
        viewService = mock(ArticleViewService.class);
        lockService = mock(RedisDistributedLockService.class);
        detailQuery = new ArticleDetailQuery(
                articleMapper,
                detailCacheStore,
                viewService,
                lockService
        );
    }

    @Test
    void shouldFallbackToMysqlWhenRedisFails() {
        BlogArticle article = article(3, "缓存降级", 20L);
        when(detailCacheStore.getState(3))
                .thenThrow(new IllegalStateException("redis unavailable"));
        when(articleMapper.selectById(3)).thenReturn(article);
        when(viewService.recordView(3L, 20L)).thenReturn(null);

        ArticleDTO result = detailQuery.findById(3);

        assertThat(result.getArticleTitle()).isEqualTo("缓存降级");
        assertThat(result.getViewCount()).isEqualTo(20L);
        verify(articleMapper).selectById(3);
    }

    @Test
    void cacheHitShouldAvoidMysqlAndUseRealtimeViewCount() {
        ArticleDTO cached = new ArticleDTO();
        cached.setId(4);
        cached.setArticleTitle("缓存命中");
        cached.setViewCount(30L);
        when(detailCacheStore.getState(4)).thenReturn(CacheValue.hit(cached));
        when(viewService.recordView(4L, 30L)).thenReturn(31L);

        ArticleDTO result = detailQuery.findById(4);

        assertThat(result.getArticleTitle()).isEqualTo("缓存命中");
        assertThat(result.getViewCount()).isEqualTo(31L);
        verifyNoInteractions(articleMapper, lockService);
    }

    @Test
    void negativeCacheShouldRejectWithoutMysql() {
        when(detailCacheStore.getState(5)).thenReturn(CacheValue.negative());

        assertThatThrownBy(() -> detailQuery.findById(5))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("文章不存在");

        verifyNoInteractions(articleMapper, lockService, viewService);
    }

    @Test
    void lockTimeoutShouldReadCacheAgainThenFallbackToMysql() {
        when(detailCacheStore.getState(6))
                .thenReturn(CacheValue.<ArticleDTO>miss())
                .thenReturn(CacheValue.<ArticleDTO>miss());
        when(detailCacheStore.lockKey(6)).thenReturn("detail-lock");
        when(lockService.tryLock(
                eq("detail-lock"),
                any(Duration.class),
                any(Duration.class)
        )).thenReturn(null);
        when(articleMapper.selectById(6)).thenReturn(article(6, "等待超时", 9L));
        when(viewService.recordView(6L, 9L)).thenReturn(10L);

        ArticleDTO result = detailQuery.findById(6);

        assertThat(result.getArticleTitle()).isEqualTo("等待超时");
        assertThat(result.getViewCount()).isEqualTo(10L);
        verify(detailCacheStore, never()).publish(eq(6), any(), any());
    }

    private BlogArticle article(int id, String title, long viewCount) {
        BlogArticle article = new BlogArticle();
        article.setId(id);
        article.setArticleTitle(title);
        article.setViewCount(viewCount);
        return article;
    }
}
