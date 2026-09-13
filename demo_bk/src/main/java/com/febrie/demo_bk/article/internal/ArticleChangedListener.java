package com.febrie.demo_bk.article.internal;

import com.febrie.demo_bk.file.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 在文章事务提交后同步可重建缓存、排行榜和物理文件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleChangedListener {

    private final ArticleCacheStore articleCacheStore;
    private final ArticleViewStore articleViewStore;
    private final FileService fileService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ArticleChangedEvent event) {
        runSafely(
                () -> articleCacheStore.evictArticleDetail(event.articleId()),
                "清理文章详情缓存失败",
                event.articleId()
        );
        runSafely(
                () -> articleCacheStore.evictArticleListDto(event.articleId()),
                "清理文章列表缓存失败",
                event.articleId()
        );
        runSafely(
                articleCacheStore::increaseLatestVersion,
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
