package com.febrie.demo_bk.identity.web;

import com.febrie.demo_bk.article.application.ArticleQueryService;
import com.febrie.demo_bk.article.application.dto.ArticleDTO;
import com.febrie.demo_bk.identity.application.AuthService;
import com.febrie.demo_bk.identity.infrastructure.security.JwtUtil;
import com.febrie.demo_bk.identity.infrastructure.security.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 通过真实 Spring Security、JWT 和 HTTP JSON 链路检查依赖升级，外部数据访问使用替身。
 */
@ActiveProfiles("dev")
@SpringBootTest(properties = {
        "spring.config.import=",
        "blog.scheduling.enabled=false",
        "blog.jwt.secret=compatibility-test-secret-at-least-32-bytes",
        "storage.local.root-path=${java.io.tmpdir}/easy-blog-compatibility-test"
})
@AutoConfigureMockMvc
class DependencySecurityCompatibilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private ArticleQueryService articleQueryService;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @Test
    void anonymousArticleShouldKeepJsonAndDateFormat() throws Exception {
        // 确认 Jackson BOM 更新没有改变公开文章接口的字段或日期格式。
        ArticleDTO article = new ArticleDTO();
        article.setId(1);
        article.setArticleTitle("兼容性检查");
        article.setArticleDate(LocalDateTime.of(2026, 10, 23, 12, 30));
        when(articleQueryService.findById(1)).thenReturn(article);

        mockMvc.perform(get("/api/public/article").param("id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.articleTitle").value("兼容性检查"))
                .andExpect(jsonPath("$.articleDate").value("2026-10-23T12:30:00"));
    }

    @Test
    void anonymousAdminRequestShouldBeRejected() throws Exception {
        // 未携带凭据时，写接口必须在进入业务处理前被拒绝。
        mockMvc.perform(post("/api/admin/content/article")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedJsonShouldFailWithBadRequest() throws Exception {
        // 畸形匿名请求应受控失败，不能使 JSON 转换器抛出未处理的服务端错误。
        mockMvc.perform(post("/api/public/login")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userName\":"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidCredentialsShouldKeepFailureContract() throws Exception {
        when(authService.authenticate("unknown", "invalid", "127.0.0.1"))
                .thenReturn(Optional.empty());
        mockMvc.perform(post("/api/public/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"unknown\",\"password\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void invalidBearerTokenShouldNotAuthenticate() throws Exception {
        // 验证 JJWT 与升级后的 Jackson 集成仍拒绝无效签名格式。
        mockMvc.perform(post("/api/admin/logout")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signedTokenShouldStillAllowLogout() throws Exception {
        String token = jwtUtil.generateToken("admin");
        mockMvc.perform(post("/api/admin/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(authService).logout(token);
    }
}
