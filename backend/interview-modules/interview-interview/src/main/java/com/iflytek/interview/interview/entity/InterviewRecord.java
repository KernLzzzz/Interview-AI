package com.iflytek.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("interview_record")
public class InterviewRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long scenarioId;
    /** 开始面试时按难度比例抽到的题目快照（JSON：[{id,title,difficulty}]），作答与评分依据 */
    private String questionData;
    private String status;
    private BigDecimal score;
    private Integer duration;
    private String answerData;
    private String aiFeedback;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}