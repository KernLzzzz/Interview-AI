package com.iflytek.interview.common.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 复合权限注解：查看用户列表
 * 组合了 @PreAuthorize —— 拥有 user:view 或 report:view 任一权限即可访问
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyAuthority('user:view', 'report:view')")
public @interface RequireUserView {
}
