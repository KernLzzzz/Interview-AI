package com.iflytek.interview.interview.vo;

public record EvaluationMetricsVO(
        int sampleSize,
        long success,
        long failed,
        long inFlight,
        double successRate,
        long averageLatencyMs,
        long p95LatencyMs,
        double retryRate,
        long remoteAiTasks,
        long localTasks) {
}
