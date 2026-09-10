package com.iflytek.interview.common.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JWT 认证过滤器：解析请求头的 Bearer Token（只含会话 key），
 * 从 Redis 还原完整身份(LoginUser)并写入 SecurityContext
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private SessionService sessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 从请求头取 token（约定带 Bearer 前缀）
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);   // 没有 token，直接放行（后续由 Security 决定 401/放行）
            return;
        }
        String token = authHeader.substring(7);

        try {
            // 2. 解析并验签，只取会话 key
            Claims claims = jwtUtil.parseToken(token);
            String sessionId = claims.get("sessionId", String.class);
            if (sessionId == null || sessionId.isEmpty()) {
                // token 里没有会话 key：按未认证处理
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            // 3. 从 Redis 还原完整身份（角色/权限以 Redis 实时状态为准）
            LoginUser loginUser = sessionService.getSession(sessionId);
            if (loginUser == null) {
                // 会话不存在/已过期（含登出后被删）：按未认证处理，后续由异常入口返回 401
                log.warn("会话不存在或已失效: sessionId={}", sessionId);
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            // 4. 组装 authorities：每个角色一个 ROLE_<r>，再加所有权限编码
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            if (loginUser.getRoles() != null) {
                loginUser.getRoles().forEach(r ->
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
            }
            if (loginUser.getPermissions() != null) {
                loginUser.getPermissions().forEach(p ->
                        authorities.add(new SimpleGrantedAuthority(p)));
            }

            // 5. 把身份封装成认证对象，写入 SecurityContext
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            loginUser,
                            null,
                            authorities
                    );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            // token 无效/过期：清空上下文，后续由异常入口返回 401
            log.warn("JWT 认证失败: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}