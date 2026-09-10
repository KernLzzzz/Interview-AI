package com.iflytek.interview.common.config;

import com.iflytek.interview.common.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 配置：无状态 + JWT 认证 + CORS 跨域
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // 开启方法级安全：@PreAuthorize / 自定义复合权限注解才生效
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS 跨域配置：允许前端来源跨域调用后端接口
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许的前端地址（改成你的前端实际地址；生产环境不要用 *）
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:5173",   // Vite 默认前端地址，按实际改
                "http://localhost:8080"    // 预留（某些前端脚手架默认 8080）
        ));
        // 允许跨域携带的请求头（JWT 的 Authorization 必须放行）
        config.setAllowedHeaders(List.of("*"));
        // 允许的 HTTP 方法（OPTIONS 是登录/请求的预检，必须包含）
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // 不使用 Cookie 认证（JWT 方案），无需携带凭证
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * 安全过滤链
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 前后端分离：开启 CORS（挂上上面的配置源，否则预检请求会被拦）
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 前后端分离：禁用 CSRF（Cookie 方案才需要）
                .csrf(AbstractHttpConfigurer::disable)
                // 禁用默认的表单登录/Basic 登录弹出框（我们要用 JWT）
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                // 无状态：不创建、不使用 Session（JWT 自包含身份）
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 放行规则（注意：从上到下匹配，放行规则要写在通用认证之前）
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/users/register", "/api/users/login").permitAll()
                        .requestMatchers("/api/evaluations/callback").permitAll()
                        .requestMatchers("/api/test/**").permitAll()
                        // 接口文档：Knife4j + OpenAPI 资源（生产环境应关闭或鉴权保护）
                        .requestMatchers("/doc.html", "/webjars/**",
                                "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .anyRequest().authenticated()
                )
                // 认证 / 授权异常统一返回 JSON（而不是跳登录页）
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json;charset=utf-8");
                            response.setStatus(401);
                            response.getWriter().write(
                                    "{\"code\":401,\"message\":\"未登录或Token已过期\",\"data\":null}"
                            );
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setContentType("application/json;charset=utf-8");
                            response.setStatus(403);
                            response.getWriter().write(
                                    "{\"code\":403,\"message\":\"无权限访问\",\"data\":null}"
                            );
                        })
                )
                // 把 JWT 过滤器插到用户名密码过滤器之前
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
