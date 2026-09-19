package com.febrie.demo_bk.article.infrastructure.scheduling;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.febrie.demo_bk.article.infrastructure.cache.ArticleViewStore;
import com.febrie.demo_bk.article.infrastructure.persistence.ArticleMapper;
import com.febrie.demo_bk.article.infrastructure.persistence.BlogArticle;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 校准累计浏览量排行榜的成员集合。
 *
 * <p>日常浏览通过 ZINCRBY 维护实时分数；本任务只补缺失成员、清除已删除成员，
 * 并把异常偏低分数提升到 MySQL 已持久化下限，不会覆盖更高的实时值。</p>
 */
@Slf4j
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "blog.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class ArticleViewRankRepairTask {

    private final ArticleMapper articleMapper;
    private final ArticleViewStore articleViewStore;

    @EventListener(ApplicationReadyEvent.class)
    public void repairAfterApplicationReady() {
        repairRankIndex();
    }

    @Scheduled(fixedDelay = 30 * 60 * 1000, initialDelay = 30 * 1000)
    public void repairRankIndex() {
        try {
            LambdaQueryWrapper<BlogArticle> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.select(BlogArticle::getId, BlogArticle::getViewCount);
            List<BlogArticle> articles = articleMapper.selectList(queryWrapper);

            Set<Integer> validIds = new HashSet<>();
            for (BlogArticle article : articles) {
                if (article.getId() == null) {
                    continue;
                }
                validIds.add(article.getId());
                articleViewStore.promoteRankScore(
                        article.getId(),
                        article.getViewCount() == null ? 0L : article.getViewCount()
                );
            }

            Set<Integer> staleIds = new HashSet<>(articleViewStore.getAllRankArticleIds());
            staleIds.removeAll(validIds);
            articleViewStore.removeRankMembers(staleIds);
        } catch (RuntimeException exception) {
            // Redis 和 MySQL 任一暂时不可用时留待下一轮重试，不能影响正常文章访问。
            log.warn("Repair article view rank failed", exception);
        }
    }
}
