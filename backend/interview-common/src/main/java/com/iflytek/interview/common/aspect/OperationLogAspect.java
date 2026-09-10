package com.iflytek.interview.common.aspect;

import com.iflytek.interview.common.annotation.LogAnnotation;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 操作日志切面：拦截标注了 @LogAnnotation 的方法，记录审计日志
 * 当前输出到控制台，可按部署需要替换为异步日志存储。
 */
@Aspect
@Component
@Slf4j
public class OperationLogAspect {

    // 切点：匹配所有标注了 @LogAnnotation 的方法
    @Pointcut("@annotation(com.iflytek.interview.common.annotation.LogAnnotation)")
    public void logPointcut() {}

    @Around("logPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {

        // 1. 从方法上读注解信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        LogAnnotation annotation = method.getAnnotation(LogAnnotation.class);
        String module = annotation.module();
        String operation = annotation.operation();

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;
            // 2. 记录模块、操作与耗时
            log.info("【审计】模块={}, 操作={}, 方法={}, 耗时={}ms",
                    module, operation, method.getName(), duration);
            return result;
        } catch (Exception e) {
            log.error("【审计】模块={}, 操作={}, 执行失败: {}",
                    module, operation, e.getMessage());
            throw e;
        }
    }
}
