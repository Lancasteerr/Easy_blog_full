package com.febrie.demo_bk.article.internal;

import com.febrie.demo_bk.file.FileService;
import com.febrie.demo_bk.shared.infrastructure.RedisDistributedLockService;
import com.febrie.demo_bk.shared.infrastructure.RedisDistributedLockService.LockHandle;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ArticleChangedListenerTest {

    @Test
    void committedTransactionShouldRunDerivedStateSynchronization() {
        ListenerFixture fixture = new ListenerFixture();

        fixture.execute(false, ArticleChangedEvent.ChangeType.CREATED);

        verify(fixture.cacheStore).evictArticleDetail(7, fixture.detailLock);
        verify(fixture.cacheStore).evictArticleListDto(7, fixture.listLock);
        verify(fixture.cacheStore).increaseLatestVersion();
        verify(fixture.viewStore).addRankMember(7, 0L);
        verify(fixture.fileService).deleteTempFiles(Set.of(11L));
    }

    @Test
    void deletionShouldPublishNegativeCacheBeforeRemovingRankMember() {
        ListenerFixture fixture = new ListenerFixture();

        fixture.execute(false, ArticleChangedEvent.ChangeType.DELETED);

        verify(fixture.cacheStore).publishMissingArticleDetail(7, fixture.detailLock);
        verify(fixture.cacheStore).publishMissingArticleListDto(7, fixture.listLock);
        verify(fixture.viewStore).removeRankMember(7);
    }

    @Test
    void rolledBackTransactionShouldNotRunCacheOrPhysicalFileActions() {
        ListenerFixture fixture = new ListenerFixture();

        fixture.execute(true, ArticleChangedEvent.ChangeType.CREATED);

        verifyNoInteractions(fixture.viewStore, fixture.fileService);
        verify(fixture.cacheStore, org.mockito.Mockito.never()).increaseLatestVersion();
    }

    @Test
    void failedCacheActionShouldNotSkipRemainingCompensationActions() {
        ListenerFixture fixture = new ListenerFixture();
        doThrow(new IllegalStateException("redis unavailable"))
                .when(fixture.cacheStore)
                .evictArticleDetail(7, fixture.detailLock);

        fixture.execute(false, ArticleChangedEvent.ChangeType.CREATED);

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
        private final RedisDistributedLockService lockService =
                mock(RedisDistributedLockService.class);
        private final LockHandle detailLock = new LockHandle("detail-lock", "detail-token");
        private final LockHandle listLock = new LockHandle("list-lock", "list-token");

        private ListenerFixture() {
            when(cacheStore.articleDetailLockKey(7)).thenReturn("detail-lock");
            when(cacheStore.articleListLockKey(7)).thenReturn("list-lock");
            when(lockService.tryLock(
                    eq("detail-lock"),
                    any(Duration.class),
                    any(Duration.class)
            )).thenReturn(detailLock);
            when(lockService.tryLock(
                    eq("list-lock"),
                    any(Duration.class),
                    any(Duration.class)
            )).thenReturn(listLock);
            when(lockService.unlock(any())).thenReturn(true);
            when(cacheStore.evictArticleDetail(7, detailLock)).thenReturn(true);
            when(cacheStore.evictArticleListDto(7, listLock)).thenReturn(true);
            when(cacheStore.publishMissingArticleDetail(7, detailLock)).thenReturn(true);
            when(cacheStore.publishMissingArticleListDto(7, listLock)).thenReturn(true);
        }

        private void execute(boolean rollback,
                             ArticleChangedEvent.ChangeType changeType) {
            try (AnnotationConfigApplicationContext context =
                         new AnnotationConfigApplicationContext()) {
                context.register(TransactionInfrastructure.class);
                context.registerBean(
                        PlatformTransactionManager.class,
                        TestTransactionManager::new
                );
                context.registerBean(
                        ArticleChangedListener.class,
                        () -> new ArticleChangedListener(
                                cacheStore,
                                viewStore,
                                fileService,
                                lockService
                        )
                );
                context.refresh();

                TransactionTemplate transactionTemplate =
                        new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                transactionTemplate.executeWithoutResult(status -> {
                    context.publishEvent(new ArticleChangedEvent(
                            7,
                            changeType,
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
