package com.iflytek.interview.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record EvaluationCallbackDTO(
        @NotNull Long taskId,
        boolean success,
        @Min(0) @Max(100) BigDecimal score,
        String feedback,
        String errorMessage) {
}
