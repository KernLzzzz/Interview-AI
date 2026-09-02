package com.iflytek.interview.question.dto;

import lombok.Data;

@Data
public class QuestionDTO {
    private Long id;
    private Long scenarioId;
    private String content;
    private String type;
    private Integer difficulty;
    private String expectedAnswer;
    private String keywords;
}
