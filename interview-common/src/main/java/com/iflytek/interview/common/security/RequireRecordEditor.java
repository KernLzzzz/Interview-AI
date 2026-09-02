package com.iflytek.interview.common.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 复合权限注解：面试评分
 * 只有拥有 record:score 权限的角色（admin / 面试官）能访问，candidate 会被拒
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyAuthority('record:score')")
public @interface RequireRecordEditor {
}
