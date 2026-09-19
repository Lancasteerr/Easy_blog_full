package com.febrie.demo_bk.article.application;

import com.febrie.demo_bk.article.application.content.ArticleContentFileExtractor;
import com.febrie.demo_bk.article.application.dto.ArticleDTO;
import com.febrie.demo_bk.article.domain.event.ArticleChangedEvent;
import com.febrie.demo_bk.article.infrastructure.persistence.ArticleMapper;
import com.febrie.demo_bk.article.infrastructure.persistence.BlogArticle;
import com.febrie.demo_bk.file.application.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleCommandServiceTest {

    private ArticleMapper articleMapper;
    private FileService fileService;
    private ArticleContentFileExtractor fileExtractor;
    private ApplicationEventPublisher eventPublisher;
    private ArticleCommandService commandService;

    @BeforeEach
    void setUp() {
        articleMapper = mock(ArticleMapper.class);
        fileService = mock(FileService.class);
        fileExtractor = mock(ArticleContentFileExtractor.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        commandService = new ArticleCommandService(
                articleMapper,
                fileService,
                fileExtractor,
                eventPublisher
        );
    }

    @Test
    void createShouldAlwaysStartViewCountAtZero() {
        ArticleDTO articleDTO = article("new-json", "new-html", null);
        articleDTO.setViewCount(999L);
        when(fileExtractor.extract("new-json", "new-html", null))
                .thenReturn(Set.of(11L));
        doAnswer(invocation -> {
            BlogArticle inserted = invocation.getArgument(0);
            inserted.setId(7);
            return 1;
        }).when(articleMapper).insert(any(BlogArticle.class));

        commandService.save(articleDTO);

        ArgumentCaptor<BlogArticle> articleCaptor = ArgumentCaptor.forClass(BlogArticle.class);
        verify(articleMapper).insert(articleCaptor.capture());
        assertThat(articleCaptor.getValue().getViewCount()).isZero();
        assertThat(articleDTO.getId()).isEqualTo(7);
        verify(fileService).markBound(Set.of(11L));

        ArticleChangedEvent event = captureEvent();
        assertThat(event.articleId()).isEqualTo(7);
        assertThat(event.changeType()).isEqualTo(ArticleChangedEvent.ChangeType.CREATED);
        assertThat(event.releasedFileIds()).isEmpty();
    }

    @Test
    void updateShouldPreserveViewCountAndReleaseOnlyRemovedFiles() {
        BlogArticle oldArticle = new BlogArticle();
        oldArticle.setId(8);
        oldArticle.setArticleContentJson("old-json");
        oldArticle.setArticleContentHtml("old-html");
        oldArticle.setViewCount(91L);
        when(articleMapper.selectById(8)).thenReturn(oldArticle);
        when(fileExtractor.extract("old-json", "old-html", null))
                .thenReturn(Set.of(1L, 2L));
        when(fileExtractor.extract("new-json", "new-html", null))
                .thenReturn(Set.of(2L, 3L));

        ArticleDTO articleDTO = article("new-json", "new-html", 8);
        articleDTO.setViewCount(0L);

        commandService.save(articleDTO);

        ArgumentCaptor<BlogArticle> articleCaptor = ArgumentCaptor.forClass(BlogArticle.class);
        verify(articleMapper).updateById(articleCaptor.capture());
        assertThat(articleCaptor.getValue().getViewCount()).isEqualTo(91L);
        verify(fileService).markBound(Set.of(2L, 3L));
        verify(fileService).markTemp(Set.of(1L));

        ArticleChangedEvent event = captureEvent();
        assertThat(event.changeType()).isEqualTo(ArticleChangedEvent.ChangeType.UPDATED);
        assertThat(event.releasedFileIds()).containsExactly(1L);
    }

    @Test
    void deleteShouldMarkReferencedFilesTemporaryBeforePublishingEvent() {
        BlogArticle article = new BlogArticle();
        article.setId(9);
        article.setArticleContentJson("json");
        article.setArticleContentHtml("html");
        when(articleMapper.selectById(9)).thenReturn(article);
        when(fileExtractor.extract("json", "html", null)).thenReturn(Set.of(21L, 22L));

        commandService.delete(9);

        verify(articleMapper).deleteById(9);
        verify(fileService).markTemp(Set.of(21L, 22L));
        ArticleChangedEvent event = captureEvent();
        assertThat(event.changeType()).isEqualTo(ArticleChangedEvent.ChangeType.DELETED);
        assertThat(event.releasedFileIds()).containsExactlyInAnyOrder(21L, 22L);
    }

    private ArticleDTO article(String json, String html, Integer id) {
        ArticleDTO articleDTO = new ArticleDTO();
        articleDTO.setId(id);
        articleDTO.setArticleTitle("标题");
        articleDTO.setArticleContentJson(json);
        articleDTO.setArticleContentHtml(html);
        return articleDTO;
    }

    private ArticleChangedEvent captureEvent() {
        ArgumentCaptor<ArticleChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(ArticleChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        return eventCaptor.getValue();
    }
}
