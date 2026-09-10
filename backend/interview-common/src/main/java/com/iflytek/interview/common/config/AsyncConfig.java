package com.iflytek.interview.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI 评测专用线程池。
 *
 * <p>拒绝策略必须保证任务不被静默丢弃。CallerRunsPolicy 会在队列饱和时
 * 由提交线程兜底执行；任务在进入线程池前已经持久化，因此即使进程异常退出，
 * 定时重试也能继续处理。</p>
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Bean("aiEvaluationExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);          // 核心线程：平时常驻
        executor.setMaxPoolSize(10);          // 最大线程：忙时扩容上限
        executor.setQueueCapacity(100);       // 队列：来不及处理先排队
        executor.setThreadNamePrefix("ai-evaluation-");
        // 队列也满了 → 由调用者线程执行（不让任务悄悄丢弃）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅关闭：等存量任务跑完再关
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
