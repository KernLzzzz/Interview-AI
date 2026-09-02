package com.iflytek.interview.common.security;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 当前登录用户：Redis 会话里存的完整身份（JwtAuthenticationFilter 还原后写入 SecurityContext）
 */
@Data
@NoArgsConstructor
public class LoginUser {

    private Long userId;

    private String username;

    /** 角色编码列表，如 ["candidate"] */
    private List<String> roles;

    /** 权限编码列表，如 ["record:view", "report:view"] */
    private List<String> permissions;

    /** 会话 ID：登录时由 SessionService 写入 Redis 的会话 key，登出时要靠它删会话 */
    private String sessionId;

    public LoginUser(Long userId, String username, List<String> roles, List<String> permissions) {
        this.userId = userId;
        this.username = username;
        this.roles = roles;
        this.permissions = permissions;
    }

    /** 便捷判断：是否拥有某个角色 */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}