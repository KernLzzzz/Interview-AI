package com.iflytek.interview.question.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题目 VO：对外返回时屏蔽 expectedAnswer（期望答案）/ keywords（评分关键词），
 * 防止把标准答案提前泄露给候选人
 */
@Data
public class QuestionVO {

    private Long id;

    private Long scenarioId;

    private String content;

    private String type;

    private Integer difficulty;

    private Integer sortOrder;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
