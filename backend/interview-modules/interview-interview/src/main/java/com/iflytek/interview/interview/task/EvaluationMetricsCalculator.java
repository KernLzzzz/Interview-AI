package com.iflytek.interview.interview.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.interview.interview.entity.TaskLog;
import com.iflytek.interview.interview.vo.EvaluationMetricsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class EvaluationMetricsCalculator {

    private final ObjectMapper objectMapper;

    public EvaluationMetricsVO calculate(List<TaskLog> tasks) {
        long success = tasks.stream().filter(task -> "success".equals(task.getStatus())).count();
        long failed = tasks.stream().filter(task -> "failed".equals(task.getStatus())).count();
        long inFlight = tasks.stream().filter(task -> "pending".equals(task.getStatus()) || "running".equals(task.getStatus())).count();
        long retried = tasks.stream().filter(task -> task.getRetryCount() != null && task.getRetryCount() > 0).count();
        List<Long> latencies = tasks.stream()
                .filter(task -> task.getStartedAt() != null && task.getFinishedAt() != null)
                .map(task -> Math.max(0, Duration.between(task.getStartedAt(), task.getFinishedAt()).toMillis()))
                .sorted().toList();
        long averageLatency = latencies.isEmpty() ? 0
                : Math.round(latencies.stream().mapToLong(Long::longValue).average().orElse(0));
        int p95Index = latencies.isEmpty() ? 0 : Math.max(0, (int) Math.ceil(latencies.size() * 0.95) - 1);
        long p95Latency = latencies.isEmpty() ? 0 : latencies.get(p95Index);
        long remote = tasks.stream().filter(task -> engine(task).equals("remote-ai")).count();
        long local = tasks.stream().filter(task -> engine(task).equals("local-explainable")).count();
        return new EvaluationMetricsVO(tasks.size(), success, failed, inFlight,
                percent(success, tasks.size()), averageLatency, p95Latency,
                percent(retried, tasks.size()), remote, local);
    }

    private double percent(long numerator, long denominator) {
        return denominator == 0 ? 0 : Math.round(numerator * 1000.0 / denominator) / 10.0;
    }

    private String engine(TaskLog task) {
        if (!StringUtils.hasText(task.getResultPayload())) return "unknown";
        try {
            return objectMapper.readTree(task.getResultPayload()).path("engine").asText("unknown");
        } catch (Exception ignored) {
            return "unknown";
        }
    }
}
