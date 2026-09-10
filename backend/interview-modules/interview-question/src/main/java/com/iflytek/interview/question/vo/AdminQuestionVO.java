package com.iflytek.interview.question.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 仅管理端使用的题目详情，包含评测所需的参考答案和关键词。 */
@Data
public class AdminQuestionVO {
    private Long id;
    private Long scenarioId;
    private String content;
    private String type;
    private Integer difficulty;
    private String expectedAnswer;
    private String keywords;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
