package com.iflytek.interview.system.controller;

import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.common.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 调试演示接口：只在 dev 环境加载，生产环境不暴露
 */
@Profile("dev")
@RestController
@RequestMapping("/api/test")
public class TestController {


    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/redis-demo")
    public Result<String> redisDemo() {
        redisTemplate.opsForValue().set("test:hello", "Hello InterviewAI!");
        Object value = redisTemplate.opsForValue().get("test:hello");
        return Result.success("写入成功，读回：" + value);
    }

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/jwt-demo")
    public Result<String> jwtDemo() {
        // 新签名：token 只带会话 key（sessionId），身份在 Redis 会话里
        String token = jwtUtil.generateToken(1L, "demo-session-id");
        Claims claims = jwtUtil.parseToken(token);
        return Result.success("token=" + token + " 解析出 sessionId=" + claims.get("sessionId"));
    }
}
