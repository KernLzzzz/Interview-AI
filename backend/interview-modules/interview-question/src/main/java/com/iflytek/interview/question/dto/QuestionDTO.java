package com.iflytek.interview.question.dto;

import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
public class QuestionDTO {
    private Long id;
    @NotNull
    private Long scenarioId;
    @NotBlank
    @Size(max = 2000)
    private String content;
    @NotBlank
    private String type;
    @NotNull
    @Min(1)
    @Max(3)
    private Integer difficulty;
    @Size(max = 5000)
    private String expectedAnswer;
    @Size(max = 500)
    private String keywords;
}
