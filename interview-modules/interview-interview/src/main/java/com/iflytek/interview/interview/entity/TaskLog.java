package com.iflytek.interview.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 异步任务日志：谁、什么任务、成功还是失败、错在哪（失败可被定时任务重扫重试）
 */
@Data
@TableName("task_log")
public class TaskLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务类型：report_generate 等 */
    private String taskType;

    /** 业务ID（如面试记录ID） */
    private Long bizId;

    /** pending/running/success/failed */
    private String status;

    /** 失败原因 */
    private String errorMessage;

    /** 已重试次数 */
    private Integer retryCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
