package com.iflytek.interview.question.task;

import com.iflytek.interview.question.config.QuestionCacheProperties;
import com.iflytek.interview.question.service.QuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionCacheWarmupTask {

    private final QuestionService questionService;
    private final QuestionCacheProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void warmupAfterStartup() {
        if (properties.isWarmupEnabled()) {
            warmup();
        }
    }

    /** 在 TTL 到期前主动刷新热点场景，避免集中冷启动。 */
    @Scheduled(fixedDelayString = "${interview.cache.question.refresh-interval:PT20M}")
    public void refreshHotBanks() {
        if (properties.isWarmupEnabled()) {
            warmup();
        }
    }

    private void warmup() {
        int count = questionService.warmupHotQuestionBanks(properties.getWarmupLimit());
        log.info("题库缓存预热完成: scenarioCount={}", count);
    }
}
