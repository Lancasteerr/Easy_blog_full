package com.febrie.demo_bk.article.application;

import com.febrie.demo_bk.article.application.content.ArticleContentFileExtractor;
import com.febrie.demo_bk.article.application.dto.ArticleDTO;
import com.febrie.demo_bk.article.domain.event.ArticleChangedEvent;
import com.febrie.demo_bk.article.infrastructure.persistence.ArticleMapper;
import com.febrie.demo_bk.article.infrastructure.persistence.BlogArticle;
import com.febrie.demo_bk.file.application.FileService;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    void updateShouldNotWriteViewCountAndReleaseOnlyRemovedFiles() {
        BlogArticle oldArticle = new BlogArticle();
        oldArticle.setId(8);
        oldArticle.setArticleContentJson("old-json");
        oldArticle.setArticleContentHtml("old-html");
        oldArticle.setViewCount(91L);
        AtomicLong persistedViewCount = new AtomicLong(91L);
        when(articleMapper.selectById(8)).thenAnswer(invocation -> {
            // 模拟编辑读取旧值后，刷盘事务先将浏览量增加 20。
            articleMapper.incrementViewCount(8, 20L);
            return oldArticle;
        });
        doAnswer(invocation -> {
            persistedViewCount.addAndGet(invocation.getArgument(1));
            return 1;
        }).when(articleMapper).incrementViewCount(8, 20L);
        doAnswer(invocation -> {
            BlogArticle updatedArticle = invocation.getArgument(0);
            // 用简单内存状态模拟 updateById 对非空 viewCount 的潜在覆盖行为。
            if (updatedArticle.getViewCount() != null) {
                persistedViewCount.set(updatedArticle.getViewCount());
            }
            return 1;
        }).when(articleMapper).updateById(any(BlogArticle.class));
        when(fileExtractor.extract("old-json", "old-html", null))
                .thenReturn(Set.of(1L, 2L));
        when(fileExtractor.extract("new-json", "new-html", null))
                .thenReturn(Set.of(2L, 3L));

        ArticleDTO articleDTO = article("new-json", "new-html", 8);
        articleDTO.setViewCount(0L);

        commandService.save(articleDTO);

        ArgumentCaptor<BlogArticle> articleCaptor = ArgumentCaptor.forClass(BlogArticle.class);
        verify(articleMapper).updateById(articleCaptor.capture());
        // 更新实体不再携带旧浏览量，避免把读取快照写回数据库。
        assertThat(articleCaptor.getValue().getViewCount()).isNull();
        assertThat(persistedViewCount).hasValue(111L);
        verify(articleMapper).incrementViewCount(8, 20L);
        verify(fileService).markBound(Set.of(2L, 3L));
        verify(fileService).markTemp(Set.of(1L));

        ArticleChangedEvent event = captureEvent();
        assertThat(event.changeType()).isEqualTo(ArticleChangedEvent.ChangeType.UPDATED);
        assertThat(event.releasedFileIds()).containsExactly(1L);
    }

    @Test
    void viewCountShouldNeverParticipateInMybatisPlusUpdates() throws NoSuchFieldException {
        Field viewCountField = BlogArticle.class.getDeclaredField("viewCount");
        TableField tableField = viewCountField.getAnnotation(TableField.class);

        assertThat(tableField).isNotNull();
        // 用实体元数据锁定浏览量字段的单一写入职责，防止后续误用 updateById。
        assertThat(tableField.updateStrategy()).isEqualTo(FieldStrategy.NEVER);
    }

    @Test
    void createShouldAllowTitleWithExactly40UnicodeCharacters() {
        ArticleDTO articleDTO = article("json", "html", null);
        articleDTO.setArticleTitle("😀".repeat(40));
        when(fileExtractor.extract("json", "html", null)).thenReturn(Set.of());
        doAnswer(invocation -> {
            BlogArticle inserted = invocation.getArgument(0);
            inserted.setId(10);
            return 1;
        }).when(articleMapper).insert(any(BlogArticle.class));

        commandService.save(articleDTO);

        ArgumentCaptor<BlogArticle> articleCaptor = ArgumentCaptor.forClass(BlogArticle.class);
        verify(articleMapper).insert(articleCaptor.capture());
        assertThat(articleCaptor.getValue().getArticleTitle())
                .isEqualTo("😀".repeat(40));
    }

    @Test
    void createShouldRejectTitleLongerThan40CharactersBeforeSideEffects() {
        ArticleDTO articleDTO = article("json", "html", null);
        articleDTO.setArticleTitle("题".repeat(41));

        assertThatThrownBy(() -> commandService.save(articleDTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文章标题不能超过40个字符");

        verifyNoInteractions(articleMapper, fileService, fileExtractor, eventPublisher);
    }

    @Test
    void updateShouldRejectOverlongUnicodeTitleBeforeLoadingOldArticle() {
        ArticleDTO articleDTO = article("json", "html", 8);
        articleDTO.setArticleTitle("😀".repeat(41));

        assertThatThrownBy(() -> commandService.save(articleDTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文章标题不能超过40个字符");

        verifyNoInteractions(articleMapper, fileService, fileExtractor, eventPublisher);
    }

    @Test
    void createShouldAllowAbstractWithExactly100UnicodeCharacters() {
        ArticleDTO articleDTO = article("json", "html", null);
        articleDTO.setArticleAbstract("😀".repeat(100));
        when(fileExtractor.extract("json", "html", null)).thenReturn(Set.of());
        doAnswer(invocation -> {
            BlogArticle inserted = invocation.getArgument(0);
            inserted.setId(10);
            return 1;
        }).when(articleMapper).insert(any(BlogArticle.class));

        commandService.save(articleDTO);

        ArgumentCaptor<BlogArticle> articleCaptor = ArgumentCaptor.forClass(BlogArticle.class);
        verify(articleMapper).insert(articleCaptor.capture());
        assertThat(articleCaptor.getValue().getArticleAbstract())
                .isEqualTo("😀".repeat(100));
    }

    @Test
    void createShouldRejectAbstractLongerThan255CharactersBeforeSideEffects() {
        ArticleDTO articleDTO = article("json", "html", null);
        articleDTO.setArticleAbstract("摘".repeat(101));

        assertThatThrownBy(() -> commandService.save(articleDTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文章概要不能超过100个字符");

        verifyNoInteractions(articleMapper, fileService, fileExtractor, eventPublisher);
    }

    @Test
    void updateShouldRejectOverlongUnicodeAbstractBeforeLoadingOldArticle() {
        ArticleDTO articleDTO = article("json", "html", 8);
        articleDTO.setArticleAbstract("😀".repeat(101));

        assertThatThrownBy(() -> commandService.save(articleDTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文章概要不能超过100个字符");

        verifyNoInteractions(articleMapper, fileService, fileExtractor, eventPublisher);
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
