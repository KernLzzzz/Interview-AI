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
    /** text / voice / video，创建后不可变。 */
    private String interviewMode;
    /** 目标岗位、JD 与候选人背景的服务端结构化快照。 */
    private String contextData;
    /** 开始面试时按难度比例抽到的题目快照（JSON：[{id,title,difficulty}]），作答与评分依据 */
    private String questionData;
    private String status;
    private BigDecimal score;
    private Integer duration;
    /** 语音或视频面试的 MinIO 文件元数据 ID。 */
    private Long mediaFileId;
    private Integer mediaDuration;
    private String answerData;
    private String aiFeedback;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
