package com.iflytek.interview.question.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties("interview.cache.question")
public class QuestionCacheProperties {

    /** Key 规范：业务域:模块:维度。最终 key 形如 interview:question-bank:scenario:12。 */
    private String keyPrefix = "interview:question-bank:scenario";

    private Duration ttl = Duration.ofMinutes(30);

    private Duration refreshInterval = Duration.ofMinutes(20);

    private boolean warmupEnabled = true;

    private int warmupLimit = 20;
}
