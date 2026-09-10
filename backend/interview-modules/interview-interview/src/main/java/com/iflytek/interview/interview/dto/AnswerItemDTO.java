package com.iflytek.interview.interview.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AnswerItemDTO(@NotNull Long questionId, @Size(max = 5000) String answer) {
}
