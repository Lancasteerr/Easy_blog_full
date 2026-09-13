package com.febrie.demo_bk.article.internal;

import com.febrie.demo_bk.file.FileService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ArticleChangedListenerTest {

    @Test
    void committedTransactionShouldRunDerivedStateSynchronization() {
        ListenerFixture fixture = new ListenerFixture();

        fixture.execute(false);

        verify(fixture.cacheStore).evictArticleDetail(7);
        verify(fixture.cacheStore).evictArticleListDto(7);
        verify(fixture.cacheStore).increaseLatestVersion();
        verify(fixture.viewStore).addRankMember(7, 0L);
        verify(fixture.fileService).deleteTempFiles(Set.of(11L));
    }

    @Test
    void rolledBackTransactionShouldNotRunCacheOrPhysicalFileActions() {
        ListenerFixture fixture = new ListenerFixture();

        fixture.execute(true);

        verifyNoInteractions(fixture.cacheStore, fixture.viewStore, fixture.fileService);
    }

    @Test
    void failedCacheActionShouldNotSkipRemainingCompensationActions() {
        ListenerFixture fixture = new ListenerFixture();
        doThrow(new IllegalStateException("redis unavailable"))
                .when(fixture.cacheStore).evictArticleDetail(7);

        fixture.execute(false);

        verify(fixture.cacheStore).increaseLatestVersion();
        verify(fixture.viewStore).addRankMember(7, 0L);
        verify(fixture.fileService).deleteTempFiles(Set.of(11L));
    }

    /**
     * 使用无资源事务管理器验证 Spring 的 AFTER_COMMIT 语义，不依赖真实数据库。
     */
    private static final class ListenerFixture {

        private final ArticleCacheStore cacheStore = mock(ArticleCacheStore.class);
        private final ArticleViewStore viewStore = mock(ArticleViewStore.class);
        private final FileService fileService = mock(FileService.class);

        private void execute(boolean rollback) {
            try (AnnotationConfigApplicationContext context =
                         new AnnotationConfigApplicationContext()) {
                context.register(TransactionInfrastructure.class);
                context.registerBean(
                        PlatformTransactionManager.class,
                        TestTransactionManager::new
                );
                context.registerBean(
                        ArticleChangedListener.class,
                        () -> new ArticleChangedListener(cacheStore, viewStore, fileService)
                );
                context.refresh();

                TransactionTemplate transactionTemplate =
                        new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                transactionTemplate.executeWithoutResult(status -> {
                    context.publishEvent(new ArticleChangedEvent(
                            7,
                            ArticleChangedEvent.ChangeType.CREATED,
                            Set.of(11L)
                    ));
                    if (rollback) {
                        status.setRollbackOnly();
                    }
                });
            }
        }
    }

    /**
     * 注册与 Spring Boot 应用一致的事务事件监听基础设施。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    private static class TransactionInfrastructure {
    }

    /**
     * 只提供事务同步边界，提交与回滚均不访问外部资源。
     */
    private static final class TestTransactionManager
            extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction,
                               TransactionDefinition definition) {
            // 测试仅需激活 Spring 事务同步。
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // 无实际资源需要提交。
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // 无实际资源需要回滚。
        }
    }
}
