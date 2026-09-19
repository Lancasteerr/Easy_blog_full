package com.febrie.demo_bk.article.application.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从编辑器 JSON、HTML 和封面字段中提取文章引用的文件 ID。
 */
@Component
@RequiredArgsConstructor
public class ArticleContentFileExtractor {

    private static final Pattern DATA_FILE_ID_PATTERN =
            Pattern.compile("data-file-id=[\"'](\\d+)[\"']");

    private final ObjectMapper objectMapper;

    public Set<Long> extract(String articleContentJson,
                             String articleContentHtml,
                             Long coverId) {
        Set<Long> fileIds = new HashSet<>();

        collectFromJson(articleContentJson, fileIds);
        collectFromHtml(articleContentHtml, fileIds);

        if (coverId != null) {
            fileIds.add(coverId);
        }

        return fileIds;
    }

    private void collectFromJson(String articleContentJson,
                                 Set<Long> fileIds) {
        if (articleContentJson == null || articleContentJson.isBlank()) {
            return;
        }

        try {
            collectImageIds(objectMapper.readTree(articleContentJson), fileIds);
        } catch (Exception ignored) {
            // 编辑器 JSON 损坏时继续使用 HTML 兜底，保持原有容错行为。
        }
    }

    private void collectFromHtml(String articleContentHtml,
                                 Set<Long> fileIds) {
        if (articleContentHtml == null || articleContentHtml.isBlank()) {
            return;
        }

        Matcher matcher = DATA_FILE_ID_PATTERN.matcher(articleContentHtml);
        while (matcher.find()) {
            fileIds.add(Long.parseLong(matcher.group(1)));
        }
    }

    private void collectImageIds(JsonNode node,
                                 Set<Long> imageIds) {
        if (node == null) {
            return;
        }

        if ("image".equals(node.path("type").asText())) {
            Long fileId = parseFileId(node.path("attrs").path("fileId"));
            if (fileId != null) {
                imageIds.add(fileId);
            }
        }

        JsonNode content = node.path("content");
        if (!content.isArray()) {
            return;
        }

        for (JsonNode child : content) {
            collectImageIds(child, imageIds);
        }
    }

    private Long parseFileId(JsonNode fileIdNode) {
        if (fileIdNode == null || fileIdNode.isNull()) {
            return null;
        }

        if (fileIdNode.isIntegralNumber()) {
            return fileIdNode.asLong();
        }

        if (fileIdNode.isTextual()) {
            try {
                return Long.parseLong(fileIdNode.asText());
            } catch (NumberFormatException ignored) {
                // 非数字字符串不是有效文件 ID，忽略即可。
            }
        }

        return null;
    }
}
