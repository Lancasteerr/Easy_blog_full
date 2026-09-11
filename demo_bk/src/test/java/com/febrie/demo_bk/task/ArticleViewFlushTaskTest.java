package com.febrie.demo_bk.task;

import com.febrie.demo_bk.dao.ArticleViewStatDAO;
import com.febrie.demo_bk.dao.BlogArticleDAO;
import com.febrie.demo_bk.service.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ArticleViewFlushTaskTest {

    private final LocalDate statDate = LocalDate.of(2026, 9, 10);

    private ArticleViewStatDAO articleViewStatDAO;
    private BlogArticleDAO blogArticleDAO;
    private ArticleViewFlushTask articleViewFlushTask;

    @BeforeEach
    void setUp() {
        articleViewStatDAO = mock(ArticleViewStatDAO.class);
        blogArticleDAO = mock(BlogArticleDAO.class);
        articleViewFlushTask = new ArticleViewFlushTask(
                mock(RedisService.class),
                articleViewStatDAO,
                blogArticleDAO
        );
    }

    @Test
    void flushShouldPersistOnlyUnflushedDelta() {
        when(articleViewStatDAO.selectPvForUpdate(101, statDate.toString())).thenReturn(80L);
        when(blogArticleDAO.incrementViewCount(101, 20L)).thenReturn(1);

        ReflectionTestUtils.invokeMethod(
                articleViewFlushTask,
                "flushArticleViewCount",
                101,
                statDate,
                100L
        );

        verify(blogArticleDAO).incrementViewCount(101, 20L);
        verify(articleViewStatDAO).updateViewCount(101, statDate.toString(), 100L);
    }

    @Test
    void flushShouldNotDuplicateAlreadyPersistedDailyPv() {
        when(articleViewStatDAO.selectPvForUpdate(101, statDate.toString())).thenReturn(100L);

        ReflectionTestUtils.invokeMethod(
                articleViewFlushTask,
                "flushArticleViewCount",
                101,
                statDate,
                100L
        );

        verifyNoInteractions(blogArticleDAO);
        verify(articleViewStatDAO, never()).updateViewCount(101, statDate.toString(), 100L);
    }
}
