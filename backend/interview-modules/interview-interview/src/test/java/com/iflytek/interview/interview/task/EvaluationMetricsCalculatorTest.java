package com.iflytek.interview.interview.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.interview.interview.entity.TaskLog;
import com.iflytek.interview.interview.vo.EvaluationMetricsVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationMetricsCalculatorTest {

    @Test
    void calculatesLatencyRetryAndEngineBreakdown() {
        LocalDateTime now = LocalDateTime.now();
        TaskLog remote = task("success", 0, now, now.plusSeconds(2), "remote-ai");
        TaskLog local = task("success", 1, now, now.plusSeconds(4), "local-explainable");
        TaskLog failed = task("failed", 2, now, null, null);

        EvaluationMetricsVO metrics = new EvaluationMetricsCalculator(new ObjectMapper())
                .calculate(List.of(remote, local, failed));

        assertThat(metrics.successRate()).isEqualTo(66.7);
        assertThat(metrics.averageLatencyMs()).isEqualTo(3000);
        assertThat(metrics.p95LatencyMs()).isEqualTo(4000);
        assertThat(metrics.retryRate()).isEqualTo(66.7);
        assertThat(metrics.remoteAiTasks()).isEqualTo(1);
        assertThat(metrics.localTasks()).isEqualTo(1);
    }

    private TaskLog task(String status, int retries, LocalDateTime started, LocalDateTime finished, String engine) {
        TaskLog task = new TaskLog();
        task.setStatus(status);
        task.setRetryCount(retries);
        task.setStartedAt(started);
        task.setFinishedAt(finished);
        if (engine != null) task.setResultPayload("{\"engine\":\"" + engine + "\"}");
        return task;
    }
}
