package com.febrie.demo_bk.config;

import com.febrie.demo_bk.filter.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

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
                                          JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception{
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
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/files/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/admin/**").authenticated()//管理接口需要认证才能访问
                        .anyRequest().authenticated()//其余所有访问都需要认证
                )
                .formLogin(form->form.disable())//禁用默认登录页
                .httpBasic(basic->basic.disable())//禁用http basic

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

    //登录认证manager
    @Bean
    public AuthenticationManager authenticationManager (AuthenticationConfiguration config)throws Exception{
        return config.getAuthenticationManager();
    }

}
