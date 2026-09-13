package com.febrie.demo_bk.article.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ArticleViewFlushTaskTest {

    private final LocalDate statDate = LocalDate.of(2026, 9, 10);

    private ArticleViewStatMapper articleViewStatMapper;
    private ArticleMapper articleMapper;
    private ArticleViewFlushTask articleViewFlushTask;

    @BeforeEach
    void setUp() {
        articleViewStatMapper = mock(ArticleViewStatMapper.class);
        articleMapper = mock(ArticleMapper.class);
        articleViewFlushTask = new ArticleViewFlushTask(
                mock(ArticleViewStore.class),
                articleViewStatMapper,
                articleMapper
        );
    }

    @Test
    void flushShouldPersistOnlyUnflushedDelta() {
        when(articleViewStatMapper.selectPvForUpdate(101, statDate.toString()))
                .thenReturn(80L);
        when(articleMapper.incrementViewCount(101, 20L)).thenReturn(1);

        articleViewFlushTask.flushArticleViewCount(101, statDate, 100L);

        verify(articleMapper).incrementViewCount(101, 20L);
        verify(articleViewStatMapper).updateViewCount(101, statDate.toString(), 100L);
    }

    @Test
    void flushShouldNotDuplicateAlreadyPersistedDailyPv() {
        when(articleViewStatMapper.selectPvForUpdate(101, statDate.toString()))
                .thenReturn(100L);

        articleViewFlushTask.flushArticleViewCount(101, statDate, 100L);

        verifyNoInteractions(articleMapper);
        verify(articleViewStatMapper, never())
                .updateViewCount(101, statDate.toString(), 100L);
    }
}
