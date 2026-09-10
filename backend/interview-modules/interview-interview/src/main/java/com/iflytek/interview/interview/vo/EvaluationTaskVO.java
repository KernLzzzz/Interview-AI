package com.iflytek.interview.interview.vo;

import com.iflytek.interview.interview.entity.TaskLog;

import java.time.LocalDateTime;

public record EvaluationTaskVO(
        Long taskId,
        Long recordId,
        String status,
        Integer retryCount,
        Integer maxRetries,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime finishedAt) {

    public static EvaluationTaskVO from(TaskLog task) {
        return new EvaluationTaskVO(task.getId(), task.getBizId(), task.getStatus(),
                task.getRetryCount(), task.getMaxRetries(), task.getErrorMessage(),
                task.getCreatedAt(), task.getUpdatedAt(), task.getFinishedAt());
    }
}
