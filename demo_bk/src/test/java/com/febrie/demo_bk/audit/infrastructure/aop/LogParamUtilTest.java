package com.febrie.demo_bk.audit.infrastructure.aop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LogParamUtilTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LogParamUtil logParamUtil = new LogParamUtil(objectMapper);

    @Test
    void parseShouldMaskSensitiveFieldsRecursivelyAndIgnoreServletObjects() throws Exception {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("articleContentHtml", "<p>secret</p>");
        nested.put("visible", "ok");

        Map<String, Object> argument = new LinkedHashMap<>();
        argument.put("Password", "123456");
        argument.put("profile", nested);
        argument.put("items", List.of(
                Map.of("accessToken", "token-value"),
                Map.of("name", "reader")
        ));

        String result = logParamUtil.parse(new Object[]{
                argument,
                mock(HttpServletRequest.class)
        });

        JsonNode root = objectMapper.readTree(result);
        assertThat(root).hasSize(1);
        assertThat(root.get(0).get("Password").asText()).isEqualTo("******");
        assertThat(root.get(0).get("profile").get("articleContentHtml").asText())
                .isEqualTo("******");
        assertThat(root.get(0).get("profile").get("visible").asText()).isEqualTo("ok");
        assertThat(root.get(0).get("items").get(0).get("accessToken").asText())
                .isEqualTo("******");
        assertThat(result).doesNotContain("123456", "token-value", "<p>secret</p>");
    }

    @Test
    void parseShouldTruncateOversizedLogPayload() {
        Map<String, Object> argument = new LinkedHashMap<>();
        argument.put("content", "a".repeat(2500));

        String result = logParamUtil.parse(new Object[]{argument});

        // 日志最大长度限制用于保护数据库字段和日志存储。
        assertThat(result).endsWith("...(truncated)");
        assertThat(result.length()).isEqualTo(2014);
    }
}
