package com.febrie.demo_bk.article.application.event;

import com.febrie.demo_bk.article.domain.event.ArticleChangedEvent;
import com.febrie.demo_bk.article.infrastructure.cache.ArticleDetailCacheStore;
import com.febrie.demo_bk.article.infrastructure.cache.ArticleListCacheStore;
import com.febrie.demo_bk.article.infrastructure.cache.ArticleViewStore;
import com.febrie.demo_bk.article.infrastructure.cache.LatestArticleIndexStore;
import com.febrie.demo_bk.file.application.FileService;
import com.febrie.demo_bk.shared.infrastructure.redis.RedisDistributedLockService;
import com.febrie.demo_bk.shared.infrastructure.redis.RedisDistributedLockService.LockHandle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;

/**
 * 在文章事务提交后同步可重建缓存、排行榜和物理文件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleChangedListener {

    private static final Duration LOCK_LEASE = Duration.ofSeconds(10L);
    private static final Duration LISTENER_LOCK_WAIT = Duration.ofSeconds(10L);

    private final ArticleDetailCacheStore detailCacheStore;
    private final ArticleListCacheStore listCacheStore;
    private final LatestArticleIndexStore latestIndexStore;
    private final ArticleViewStore articleViewStore;
    private final FileService fileService;
    private final RedisDistributedLockService lockService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ArticleChangedEvent event) {
        runSafely(
                () -> synchronizeDetailCache(event),
                "同步文章详情缓存失败",
                event.articleId()
        );
        runSafely(
                () -> synchronizeListCache(event),
                "同步文章列表缓存失败",
                event.articleId()
        );
        runSafely(
                latestIndexStore::increaseVersion,
                "更新最新文章列表版本失败",
                event.articleId()
        );

        if (event.changeType() == ArticleChangedEvent.ChangeType.CREATED) {
            runSafely(
                    () -> articleViewStore.addRankMember(event.articleId(), 0L),
                    "新增文章排行榜成员失败",
                    event.articleId()
            );
        } else if (event.changeType() == ArticleChangedEvent.ChangeType.DELETED) {
            runSafely(
                    () -> articleViewStore.removeRankMember(event.articleId()),
                    "删除文章排行榜成员失败",
                    event.articleId()
            );
        }

        runSafely(
                () -> fileService.deleteTempFiles(event.releasedFileIds()),
                "清理文章已释放文件失败",
                event.articleId()
        );
    }

    private void synchronizeDetailCache(ArticleChangedEvent event) {
        LockHandle lockHandle = requireLock(
                detailCacheStore.lockKey(event.articleId())
        );
        try {
            boolean success = event.changeType() == ArticleChangedEvent.ChangeType.DELETED
                    ? detailCacheStore.publishMissing(
                            event.articleId(),
                            lockHandle
                    )
                    : detailCacheStore.evict(event.articleId(), lockHandle);
            requireCacheTransition(success, "文章详情缓存");
        } finally {
            unlockSafely(lockHandle);
        }
    }

    private void synchronizeListCache(ArticleChangedEvent event) {
        LockHandle lockHandle = requireLock(
                listCacheStore.lockKey(event.articleId())
        );
        try {
            boolean success = event.changeType() == ArticleChangedEvent.ChangeType.DELETED
                    ? listCacheStore.publishMissing(
                            event.articleId(),
                            lockHandle
                    )
                    : listCacheStore.evict(event.articleId(), lockHandle);
            requireCacheTransition(success, "文章列表缓存");
        } finally {
            unlockSafely(lockHandle);
        }
    }

    private LockHandle requireLock(String lockKey) {
        LockHandle lockHandle = lockService.tryLock(
                lockKey,
                LOCK_LEASE,
                LISTENER_LOCK_WAIT
        );
        if (lockHandle == null) {
            throw new IllegalStateException("等待缓存同步锁超时，lockKey=" + lockKey);
        }
        return lockHandle;
    }

    private void requireCacheTransition(boolean success, String operation) {
        if (!success) {
            throw new IllegalStateException(operation + "因锁所有权变化而被拒绝");
        }
    }

    private void unlockSafely(LockHandle lockHandle) {
        try {
            if (!lockService.unlock(lockHandle)) {
                log.debug("缓存同步锁已过期或所有权已变化，lockKey={}", lockHandle.key());
            }
        } catch (RuntimeException exception) {
            log.warn("释放缓存同步锁失败，lockKey={}", lockHandle.key(), exception);
        }
    }

    private void runSafely(Runnable action,
                           String operation,
                           int articleId) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            // 缓存和物理文件都是可修复的派生状态，不得影响已提交的文章事务。
            log.warn("{}, articleId={}", operation, articleId, exception);
        }
    }
}
