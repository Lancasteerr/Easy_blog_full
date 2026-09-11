package com.febrie.demo_bk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.febrie.demo_bk.dao.BlogArticleDAO;
import com.febrie.demo_bk.service.pv.ArticleViewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BlogArticleServiceJsonTest {

    private BlogArticleService blogArticleService;

    @BeforeEach
    void setUp() {
        // 只验证JSON解析辅助逻辑，依赖对象使用mock避免连接数据库或Redis。
        blogArticleService = new BlogArticleService(
                mock(BlogArticleDAO.class),
                mock(RedisService.class),
                mock(FileService.class),
                mock(ArticleViewServiceImpl.class),
                mock(ArticleIndexRedisService.class),
                new ObjectMapper()
        );
    }

    @Test
    void collectImageIdsShouldReadNumericAndTextualFileIdsFromJacksonTree() {
        String articleContentJson = """
                {
                  "type": "doc",
                  "content": [
                    {
                      "type": "image",
                      "attrs": {
                        "fileId": 11
                      }
                    },
                    {
                      "type": "paragraph",
                      "content": [
                        {
                          "type": "image",
                          "attrs": {
                            "fileId": "12"
                          }
                        },
                        {
                          "type": "image",
                          "attrs": {
                            "fileId": "bad-id"
                          }
                        },
                        {
                          "type": "image",
                          "attrs": {}
                        }
                      ]
                    }
                  ]
                }
                """;

        Set<Long> imageIds = ReflectionTestUtils.invokeMethod(
                blogArticleService,
                "collectImageIds",
                articleContentJson,
                "<img data-file-id=\"13\"><img data-file-id='14'>"
        );

        assertThat(imageIds).containsExactlyInAnyOrder(11L, 12L, 13L, 14L);
    }

    @Test
    void collectImageIdsShouldIgnoreInvalidJsonAndKeepHtmlFallback() {
        Set<Long> imageIds = ReflectionTestUtils.invokeMethod(
                blogArticleService,
                "collectImageIds",
                "{bad json",
                "<figure data-file-id=\"21\"></figure>"
        );

        // 编辑器JSON异常时不能阻塞文章保存，HTML里的文件ID仍需被回收/绑定逻辑识别。
        assertThat(imageIds).containsExactly(21L);
    }
}
