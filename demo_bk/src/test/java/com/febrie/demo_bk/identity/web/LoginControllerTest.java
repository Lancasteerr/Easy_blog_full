package com.febrie.demo_bk.identity.web;

import com.febrie.demo_bk.identity.application.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证登录 HTTP 边界只使用专用请求对象，同时保持原有接口协议。
 */
class LoginControllerTest {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LoginController(authService))
                .build();
    }

    @Test
    @DisplayName("登录请求应绑定原有字段并传递真实客户端 IP")
    void loginShouldBindRequestAndDelegateAuthentication() throws Exception {
        when(authService.authenticate("admin", "password", "203.0.113.9"))
                .thenReturn(Optional.of("jwt-token"));

        mockMvc.perform(post("/api/public/login")
                        .header("X-Forwarded-For", "203.0.113.9, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "admin",
                                  "password": "password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.token").value("jwt-token"));

        verify(authService).authenticate("admin", "password", "203.0.113.9");
    }
}
