package com.febrie.demo_bk.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.febrie.demo_bk.result.ApiError;
import com.febrie.demo_bk.service.TokenBlacklistService;
import com.febrie.demo_bk.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 解析 JWT 并验证它是否有效
 */

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    private final TokenBlacklistService tokenBlacklistService;

    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   TokenBlacklistService tokenBlacklistService,
                                   ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.tokenBlacklistService = tokenBlacklistService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        //如果是无需认证的api访问 跳过token检验
        if("OPTIONS".equalsIgnoreCase(request.getMethod())
                || path.startsWith("/api/public/")
                || path.startsWith("/files/")){
            filterChain.doFilter(request,response);//跳过，不做token校验
            return;
        }

        String token = request.getHeader("Authorization");

        if(token != null && token.startsWith("Bearer ")){
            token = token.substring(7);
            try {
                Claims claims = jwtUtil.parsePayload(token);

                if(jwtUtil.isTokenExpired(claims)
                        || tokenBlacklistService.isRevoked(token, claims)) {
                    writeUnauthorizedResponse(request, response);
                    return;
                }

                String userName = claims.getSubject();

                if(userName != null) {
                    /**
                     * 从 Spring Security 6 开始，必须显式设置 SecurityContextRepository
                     * 这里可以提取角色和权限信息
                     */
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userName,null,null);

                    //设置请求的认证信息
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }catch (Exception e){
                // Token 无效或过期时统一返回 401 JSON，让前端只按 HTTP 状态判断认证失败。
                writeUnauthorizedResponse(request, response);
                return;
            }
        }

        filterChain.doFilter(request,response);
    }

    private void writeUnauthorizedResponse(HttpServletRequest request,
                                           HttpServletResponse response) throws IOException {
        // 返回认证失败前清空上下文，避免后续处理误用旧认证信息。
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        objectMapper.writeValue(
                response.getWriter(),
                ApiError.of(
                        HttpStatus.UNAUTHORIZED,
                        "登录状态已失效，请重新登录",
                        request.getRequestURI()
                )
        );
    }
}
