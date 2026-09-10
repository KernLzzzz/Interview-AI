package com.iflytek.interview.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.interview.config.EvaluationProperties;
import com.iflytek.interview.interview.dto.EvaluationCallbackDTO;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.entity.TaskLog;
import com.iflytek.interview.interview.mapper.InterviewRecordMapper;
import com.iflytek.interview.interview.service.EvaluationTaskService;
import com.iflytek.interview.interview.service.TaskLogService;
import com.iflytek.interview.interview.task.EvaluationWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EvaluationTaskServiceImpl implements EvaluationTaskService {

    private final TaskLogService taskLogService;
    private final InterviewRecordMapper interviewRecordMapper;
    private final EvaluationWorker evaluationWorker;
    private final EvaluationProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public TaskLog submit(Long recordId, String callbackUrl) {
        InterviewRecord record = interviewRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        if (!"completed".equals(record.getStatus())) {
            throw new BusinessException(400, "请先提交面试答案");
        }
        TaskLog active = latestForRecord(recordId);
        if (active != null && List.of("pending", "running", "success").contains(active.getStatus())) {
            return active;
        }

        LocalDateTime now = LocalDateTime.now();
        TaskLog task = new TaskLog();
        task.setTaskType("ai_evaluation");
        task.setBizId(recordId);
        task.setStatus("pending");
        task.setRetryCount(0);
        task.setMaxRetries(properties.getMaxRetries());
        task.setCallbackUrl(callbackUrl);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskLogService.save(task);
        dispatchAfterCommit(task.getId());
        return task;
    }

    @Override
    public TaskLog get(Long taskId) {
        TaskLog task = taskLogService.getById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.EVALUATION_TASK_NOT_FOUND);
        }
        return task;
    }

    @Override
    public TaskLog latestForRecord(Long recordId) {
        return taskLogService.getOne(new LambdaQueryWrapper<TaskLog>()
                .eq(TaskLog::getTaskType, "ai_evaluation")
                .eq(TaskLog::getBizId, recordId)
                .orderByDesc(TaskLog::getId)
                .last("LIMIT 1"), false);
    }

    @Override
    @Transactional
    public void completeFromCallback(String token, EvaluationCallbackDTO callback) {
        if (!StringUtils.hasText(properties.getCallbackToken())
                || token == null
                || !MessageDigest.isEqual(
                        properties.getCallbackToken().getBytes(StandardCharsets.UTF_8),
                        token.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.EVALUATION_CALLBACK_UNAUTHORIZED);
        }
        TaskLog task = get(callback.taskId());
        if ("success".equals(task.getStatus())) {
            return;
        }
        if (!callback.success()) {
            task.setStatus("failed");
            task.setErrorMessage(callback.errorMessage());
            task.setNextRetryAt(LocalDateTime.now().plus(properties.getRetryDelay()));
            taskLogService.updateById(task);
            return;
        }
        if (callback.score() == null || !StringUtils.hasText(callback.feedback())) {
            throw new BusinessException(400, "成功回调必须包含 score 和 feedback");
        }
        try {
            objectMapper.readTree(callback.feedback());
        } catch (Exception ex) {
            throw new BusinessException(400, "feedback 必须是合法 JSON");
        }
        InterviewRecord record = interviewRecordMapper.selectById(task.getBizId());
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        record.setScore(callback.score());
        record.setAiFeedback(callback.feedback());
        interviewRecordMapper.updateById(record);
        task.setStatus("success");
        task.setResultPayload(callback.feedback());
        task.setFinishedAt(LocalDateTime.now());
        task.setNextRetryAt(null);
        taskLogService.updateById(task);
    }

    @Override
    @Transactional
    public int retryDueTasks() {
        List<TaskLog> due = taskLogService.list(new LambdaQueryWrapper<TaskLog>()
                .eq(TaskLog::getTaskType, "ai_evaluation")
                .eq(TaskLog::getStatus, "failed")
                .lt(TaskLog::getRetryCount, properties.getMaxRetries())
                .and(wrapper -> wrapper.isNull(TaskLog::getNextRetryAt)
                        .or().le(TaskLog::getNextRetryAt, LocalDateTime.now()))
                .last("LIMIT 100"));
        int claimed = 0;
        for (TaskLog task : due) {
            boolean updated = taskLogService.update(new LambdaUpdateWrapper<TaskLog>()
                    .eq(TaskLog::getId, task.getId())
                    .eq(TaskLog::getStatus, "failed")
                    .set(TaskLog::getStatus, "pending")
                    .set(TaskLog::getRetryCount, task.getRetryCount() + 1));
            if (updated) {
                claimed++;
                dispatchAfterCommit(task.getId());
            }
        }
        // 进程在事务提交后、投递前退出时会留下 pending；工作进程异常退出会留下 running。
        // 扫描器会恢复这些悬挂任务，worker 的条件更新确保多实例下只有一个执行者成功领取。
        List<TaskLog> stale = taskLogService.list(new LambdaQueryWrapper<TaskLog>()
                .eq(TaskLog::getTaskType, "ai_evaluation")
                .lt(TaskLog::getRetryCount, properties.getMaxRetries())
                .and(wrapper -> wrapper
                        .nested(pending -> pending.eq(TaskLog::getStatus, "pending")
                                .le(TaskLog::getCreatedAt, LocalDateTime.now().minusMinutes(1)))
                        .or(stuck -> stuck.eq(TaskLog::getStatus, "running")
                                .le(TaskLog::getStartedAt, LocalDateTime.now().minusMinutes(10))))
                .last("LIMIT 100"));
        for (TaskLog task : stale) {
            if ("running".equals(task.getStatus())) {
                boolean recovered = taskLogService.update(new LambdaUpdateWrapper<TaskLog>()
                        .eq(TaskLog::getId, task.getId())
                        .eq(TaskLog::getStatus, "running")
                        .set(TaskLog::getStatus, "pending")
                        .set(TaskLog::getRetryCount, task.getRetryCount() + 1));
                if (!recovered) {
                    continue;
                }
            }
            claimed++;
            dispatchAfterCommit(task.getId());
        }
        return claimed;
    }

    private void dispatchAfterCommit(Long taskId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evaluationWorker.execute(taskId);
                }
            });
        } else {
            evaluationWorker.execute(taskId);
        }
    }
}
