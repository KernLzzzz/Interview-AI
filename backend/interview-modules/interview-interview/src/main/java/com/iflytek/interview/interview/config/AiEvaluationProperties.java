package com.iflytek.interview.interview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("interview.evaluation.ai")
public class AiEvaluationProperties {
    /** 未启用或未配置 apiKey 时使用本地可解释评测器。 */
    private boolean enabled = false;
    private String baseUrl = "https://api.openai.com/v1";
    private String endpoint = "/chat/completions";
    private String model;
    private String apiKey;
    private int connectTimeoutSeconds = 10;
    private int readTimeoutSeconds = 90;
}
