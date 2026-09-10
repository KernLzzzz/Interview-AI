package com.iflytek.interview.interview.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EvaluationSubmitDTO(@NotNull Long recordId, @Size(max = 500) String callbackUrl) {
}
