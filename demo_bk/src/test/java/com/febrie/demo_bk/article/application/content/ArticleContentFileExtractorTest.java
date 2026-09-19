package com.febrie.demo_bk.article.application.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleContentFileExtractorTest {

    private final ArticleContentFileExtractor extractor =
            new ArticleContentFileExtractor(new ObjectMapper());

    @Test
    void extractShouldReadJsonHtmlAndCoverFileIds() {
        String articleContentJson = """
                {
                  "type": "doc",
                  "content": [
                    {"type": "image", "attrs": {"fileId": 11}},
                    {"type": "paragraph", "content": [
                      {"type": "image", "attrs": {"fileId": "12"}},
                      {"type": "image", "attrs": {"fileId": "bad-id"}}
                    ]}
                  ]
                }
                """;

        Set<Long> fileIds = extractor.extract(
                articleContentJson,
                "<img data-file-id=\"13\"><img data-file-id='14'>",
                15L
        );

        assertThat(fileIds).containsExactlyInAnyOrder(11L, 12L, 13L, 14L, 15L);
    }

    @Test
    void extractShouldIgnoreInvalidJsonAndKeepHtmlFallback() {
        Set<Long> fileIds = extractor.extract(
                "{bad json",
                "<figure data-file-id=\"21\"></figure>",
                null
        );

        // 编辑器 JSON 异常时不能阻塞文章保存，HTML 中的文件 ID 仍需被识别。
        assertThat(fileIds).containsExactly(21L);
    }
}
