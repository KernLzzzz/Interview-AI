package com.iflytek.interview.interview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SubmitInterviewDTO(@NotEmpty List<@Valid AnswerItemDTO> answers) {
}
