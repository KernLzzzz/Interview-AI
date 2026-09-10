package com.iflytek.interview.interview.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateInterviewDTO(
        @NotNull Long scenarioId,
        @Pattern(regexp = "text|voice|video", message = "面试模式仅支持 text、voice 或 video")
        String interviewMode,
        @Size(max = 80, message = "目标岗位不能超过80字") String targetRole,
        @Size(max = 6000, message = "岗位描述不能超过6000字") String jobDescription,
        @Size(max = 6000, message = "候选人背景不能超过6000字") String candidateBackground) {

    public String normalizedMode() {
        return interviewMode == null || interviewMode.isBlank() ? "text" : interviewMode;
    }

    public String safeTargetRole() { return targetRole == null ? "" : targetRole.trim(); }
    public String safeJobDescription() { return jobDescription == null ? "" : jobDescription.trim(); }
    public String safeCandidateBackground() { return candidateBackground == null ? "" : candidateBackground.trim(); }
}
