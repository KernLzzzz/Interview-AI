package com.iflytek.interview.interview.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateInterviewDTO(
        @NotNull Long scenarioId,
        @Pattern(regexp = "text|voice|video", message = "面试模式仅支持 text、voice 或 video")
        String interviewMode) {

    public String normalizedMode() {
        return interviewMode == null || interviewMode.isBlank() ? "text" : interviewMode;
    }
}
