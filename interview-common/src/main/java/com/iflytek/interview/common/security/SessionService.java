package com.iflytek.interview.common.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 登录会话服务：把身份(LoginUser)存进 Redis，JWT 只携带会话 key。
 * 登录(写)、认证过滤器(读)、登出(删)三处共用，保证"改权限/下线即时生效"。
 */
@Service
public class SessionService {

    private static final String SESSION_KEY_PREFIX = "login:session:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /** 会话有效期，与 JWT 过期时间一致（毫秒） */
    @Value("${jwt.expiration}")
    private long sessionTimeout;

    /**
     * 创建会话：写入 Redis（TTL = JWT 过期时间），返回 sessionId
     */
    public String createSession(LoginUser loginUser) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        loginUser.setSessionId(sessionId);
        redisTemplate.opsForValue().set(SESSION_KEY_PREFIX + sessionId, loginUser,
                sessionTimeout, TimeUnit.MILLISECONDS);
        return sessionId;
    }

    /**
     * 按 sessionId 还原会话，不存在或已过期返回 null
     */
    public LoginUser getSession(String sessionId) {
        Object value = redisTemplate.opsForValue().get(SESSION_KEY_PREFIX + sessionId);
        return value instanceof LoginUser loginUser ? loginUser : null;
    }

    /**
     * 删除会话：登出后该 token 立即失效
     */
    public void removeSession(String sessionId) {
        if (sessionId != null && !sessionId.isEmpty()) {
            redisTemplate.delete(SESSION_KEY_PREFIX + sessionId);
        }
    }

    /**
     * 删除某用户的全部会话：改密码/改角色后调用，旧 token 立即全部失效（强制重新登录）。
     * 场景：该用户可能在多端登录（Web/手机），只删当前 sessionId 不够。
     * 注：教学项目用 KEYS 全量扫即可；生产海量会话应改用 SCAN 或维护 user_id → session 索引。
     */
    public void removeUserSessions(Long userId) {
        Set<String> keys = redisTemplate.keys(SESSION_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : keys) {
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof LoginUser loginUser && userId.equals(loginUser.getUserId())) {
                redisTemplate.delete(key);
            }
        }
    }
}
