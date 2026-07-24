package com.febrie.demo_bk.filter;

import com.febrie.demo_bk.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 解析 JWT 并验证它是否有效
 */

public class JwtAuthenticationFilter extends OncePerRequestFilter {

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
                if(!JwtUtil.isTokenExpired(token)) {
                    Claims claims = JwtUtil.parsePayload(token);
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
                }
            }catch (Exception e){
                //解析token出错
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("Invalid or expired token");
                return;
            }
        }

        filterChain.doFilter(request,response);
    }
}
