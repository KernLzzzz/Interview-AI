package com.iflytek.interview.interview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties("interview.evaluation")
public class EvaluationProperties {
    private int maxRetries = 3;
    private Duration retryDelay = Duration.ofMinutes(1);
    private String callbackToken;
}
