package com.febrie.demo_bk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.febrie.demo_bk.filter.JwtAuthenticationFilter;
import com.febrie.demo_bk.result.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Value("${blog.cors.allowed-origins}")
    private String corsAllowedOrigins;

    //密码加密方式
    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    //设置api访问权限
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                          JwtAuthenticationFilter jwtAuthenticationFilter,
                                          ObjectMapper objectMapper) throws Exception{
        http
                .cors(cors -> cors
                        .configurationSource(request -> {
                            CorsConfiguration config = new CorsConfiguration();
                            // CORS允许域名由不同Profile配置，避免生产环境继续放行开发地址。
                            config.setAllowedOrigins(resolveAllowedOrigins());
                            config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                            config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
                            config.setAllowCredentials(false);
                            config.setMaxAge(3600L);
                            return config;
                        }))
                .csrf(AbstractHttpConfigurer::disable)//csrf disable
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/files/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/admin/**").authenticated()//管理接口需要认证才能访问
                        .anyRequest().authenticated()//其余所有访问都需要认证
                )
                .exceptionHandling(exception -> exception
                        // 未登录访问受保护接口时返回标准 JSON，避免前端收到默认 HTML 或空响应。
                        .authenticationEntryPoint((request, response, authException) ->
                                writeSecurityError(
                                        response,
                                        request,
                                        objectMapper,
                                        HttpStatus.UNAUTHORIZED,
                                        "请先登录"
                                ))
                        // 已登录但权限不足时使用 403，和未登录 401 区分开。
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeSecurityError(
                                        response,
                                        request,
                                        objectMapper,
                                        HttpStatus.FORBIDDEN,
                                        "没有权限访问该资源"
                                ))
                )
                .formLogin(AbstractHttpConfigurer::disable)//禁用默认登录页
                .httpBasic(AbstractHttpConfigurer::disable)//禁用http basic

                // 添加自定义的 JWT 认证过滤器，确保在 UsernamePasswordAuthenticationFilter 之前执行
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private List<String> resolveAllowedOrigins() {
        List<String> origins =
                Arrays.stream(corsAllowedOrigins.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isBlank())
                        .toList();

        if (origins.isEmpty()) {
            throw new IllegalStateException("BLOG_CORS_ALLOWED_ORIGINS不能为空，请配置允许访问后端的前端域名");
        }

        return origins;
    }

    private void writeSecurityError(HttpServletResponse response,
                                    HttpServletRequest request,
                                    ObjectMapper objectMapper,
                                    HttpStatus status,
                                    String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        objectMapper.writeValue(
                response.getWriter(),
                ApiError.of(
                        status,
                        message,
                        request.getRequestURI()
                )
        );
    }

    //登录认证manager
    @Bean
    public AuthenticationManager authenticationManager (AuthenticationConfiguration config)throws Exception{
        return config.getAuthenticationManager();
    }

}
