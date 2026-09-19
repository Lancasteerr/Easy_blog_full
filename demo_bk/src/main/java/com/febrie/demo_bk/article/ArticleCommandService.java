package com.febrie.demo_bk.article;

import com.febrie.demo_bk.article.internal.ArticleChangedEvent;
import com.febrie.demo_bk.article.internal.ArticleContentFileExtractor;
import com.febrie.demo_bk.article.internal.ArticleMapper;
import com.febrie.demo_bk.article.internal.BlogArticle;
import com.febrie.demo_bk.file.FileService;
import com.febrie.demo_bk.shared.web.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 文章写入用例入口，负责事务内的文章和文件状态变更。
 */
@Service
@RequiredArgsConstructor
public class ArticleCommandService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final ArticleMapper articleMapper;
    private final FileService fileService;
    private final ArticleContentFileExtractor fileExtractor;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 新增或更新文章。是否新增由 DTO 是否包含 ID 判断。
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(ArticleDTO articleDTO) {
        if (articleDTO == null) {
            throw new IllegalArgumentException("文章内容不能为空");
        }

        BlogArticle oldArticle = findOldArticle(articleDTO.getId());
        Set<Long> oldFileIds = extractFileIds(oldArticle);

        setCoverUrl(articleDTO);
        Set<Long> newFileIds = fileExtractor.extract(
                articleDTO.getArticleContentJson(),
                articleDTO.getArticleContentHtml(),
                articleDTO.getArticleCover()
        );
        fileService.validateImageFiles(newFileIds);

        boolean newArticle = articleDTO.getId() == null;
        articleDTO.setArticleDate(LocalDateTime.now(BUSINESS_ZONE));
        BlogArticle article = ArticleConverter.toEntity(articleDTO);

        if (newArticle) {
            // 浏览量只能由浏览统计维护，不能信任管理端传入的值。
            article.setViewCount(0L);
            articleMapper.insert(article);
            articleDTO.setId(article.getId());
        } else {
            // 编辑文章不得覆盖已经累计的浏览量。
            article.setViewCount(oldArticle.getViewCount());
            articleMapper.updateById(article);
        }

        fileService.markBound(newFileIds);
        Set<Long> releasedFileIds = releaseRemovedFiles(oldFileIds, newFileIds);

        eventPublisher.publishEvent(new ArticleChangedEvent(
                articleDTO.getId(),
                newArticle
                        ? ArticleChangedEvent.ChangeType.CREATED
                        : ArticleChangedEvent.ChangeType.UPDATED,
                releasedFileIds
        ));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(int articleId) {
        validateArticleId(articleId);

        BlogArticle article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new ResourceNotFoundException("文章不存在");
        }

        Set<Long> releasedFileIds = extractFileIds(article);
        articleMapper.deleteById(articleId);
        fileService.markTemp(releasedFileIds);

        eventPublisher.publishEvent(new ArticleChangedEvent(
                articleId,
                ArticleChangedEvent.ChangeType.DELETED,
                releasedFileIds
        ));
    }

    private BlogArticle findOldArticle(Integer articleId) {
        if (articleId == null) {
            return null;
        }

        validateArticleId(articleId);
        BlogArticle oldArticle = articleMapper.selectById(articleId);
        if (oldArticle == null) {
            throw new ResourceNotFoundException("文章不存在");
        }
        return oldArticle;
    }

    private void setCoverUrl(ArticleDTO articleDTO) {
        Long coverId = articleDTO.getArticleCover();
        articleDTO.setCoverObjectUrl(
                coverId == null ? null : fileService.getImageUrl(coverId)
        );
    }

    private Set<Long> extractFileIds(BlogArticle article) {
        if (article == null) {
            return Collections.emptySet();
        }
        return fileExtractor.extract(
                article.getArticleContentJson(),
                article.getArticleContentHtml(),
                article.getArticleCover()
        );
    }

    private Set<Long> releaseRemovedFiles(Set<Long> oldFileIds,
                                          Set<Long> newFileIds) {
        if (oldFileIds == null || oldFileIds.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Long> releasedFileIds = new HashSet<>(oldFileIds);
        releasedFileIds.removeAll(newFileIds);
        fileService.markTemp(releasedFileIds);
        return releasedFileIds;
    }

    private void validateArticleId(int articleId) {
        if (articleId <= 0) {
            throw new IllegalArgumentException("文章ID不合法");
        }
    }
}
