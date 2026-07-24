package com.febrie.demo_bk.config;

import com.febrie.demo_bk.filter.JwtAuthenticationFilter;
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

@Configuration
public class SecurityConfig {

    //密码加密方式
    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    //设置api访问权限
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception{
        http
                .cors(cors -> cors
                        .configurationSource(request -> {
                            CorsConfiguration config = new CorsConfiguration();
                            config.addAllowedOrigin("http://localhost:8080");
                            config.setAllowedMethods(Arrays.asList("GET","POST","PUT","DELETE","OPTIONS"));
                            config.addAllowedHeader("Authorization");
                            config.addAllowedHeader("Content-Type");
                            config.setAllowCredentials(true);
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
                .addFilterBefore(new JwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    //登录认证manager
    @Bean
    public AuthenticationManager authenticationManager (AuthenticationConfiguration config)throws Exception{
        return config.getAuthenticationManager();
    }

}
