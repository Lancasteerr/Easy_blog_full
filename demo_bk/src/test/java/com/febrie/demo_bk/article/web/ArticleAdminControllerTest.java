package com.febrie.demo_bk.article.web;

import com.febrie.demo_bk.article.application.ArticleCommandService;
import com.febrie.demo_bk.article.application.dto.ArticleDTO;
import com.febrie.demo_bk.shared.web.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证文章管理接口保留成功协议，并将写入校验错误转换为统一的 HTTP 错误响应。
 */
class ArticleAdminControllerTest {

    private ArticleCommandService articleCommandService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        articleCommandService = mock(ArticleCommandService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ArticleAdminController(articleCommandService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void saveShouldKeepExistingSuccessResponse() throws Exception {
        mockMvc.perform(post("/api/admin/content/article")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "articleTitle": "标题",
                                  "articleAbstract": "概要"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(articleCommandService).save(any(ArticleDTO.class));
    }

    @Test
    void saveShouldReturnBadRequestWhenAbstractIsTooLong() throws Exception {
        doThrow(new IllegalArgumentException("文章概要不能超过255个字符"))
                .when(articleCommandService)
                .save(any(ArticleDTO.class));

        mockMvc.perform(post("/api/admin/content/article")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "articleTitle": "标题",
                                  "articleAbstract": "超长概要"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("文章概要不能超过255个字符"))
                .andExpect(jsonPath("$.path").value("/api/admin/content/article"));
    }

    @Test
    void saveShouldReturnBadRequestWhenTitleIsTooLong() throws Exception {
        doThrow(new IllegalArgumentException("文章标题不能超过255个字符"))
                .when(articleCommandService)
                .save(any(ArticleDTO.class));

        mockMvc.perform(post("/api/admin/content/article")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "articleTitle": "超长标题",
                                  "articleAbstract": "概要"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("文章标题不能超过255个字符"))
                .andExpect(jsonPath("$.path").value("/api/admin/content/article"));
    }
}
