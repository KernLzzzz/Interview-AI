package com.iflytek.interview.interview.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.iflytek.interview.interview.config.EvaluationProperties;
import com.iflytek.interview.interview.entity.TaskLog;
import com.iflytek.interview.interview.service.TaskLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class EvaluationWorker {

    private final TaskLogService taskLogService;
    private final ReportGenerator reportGenerator;
    private final EvaluationProperties properties;

    @Async("aiEvaluationExecutor")
    public void execute(Long taskId) {
        LocalDateTime now = LocalDateTime.now();
        boolean claimed = taskLogService.update(new LambdaUpdateWrapper<TaskLog>()
                .eq(TaskLog::getId, taskId)
                .eq(TaskLog::getStatus, "pending")
                .set(TaskLog::getStatus, "running")
                .set(TaskLog::getStartedAt, now)
                .set(TaskLog::getErrorMessage, null));
        if (!claimed) {
            log.debug("评测任务已被其他工作线程领取: taskId={}", taskId);
            return;
        }

        TaskLog task = taskLogService.getById(taskId);
        // 评测耗时是异步化收益的直接依据（同步执行时这部分会叠加到接口响应上），
        // 也是发现评测退化的第一手信号，因此显式记录。
        long startedMs = System.currentTimeMillis();
        try {
            String result = reportGenerator.generate(task.getBizId());
            task.setStatus("success");
            task.setResultPayload(result);
            task.setFinishedAt(LocalDateTime.now());
            task.setNextRetryAt(null);
            taskLogService.updateById(task);
            log.info("评测任务完成: taskId={}, recordId={}, 耗时={}ms",
                    taskId, task.getBizId(), System.currentTimeMillis() - startedMs);
        } catch (Exception ex) {
            log.error("AI 评测失败: taskId={}, recordId={}, 耗时={}ms",
                    taskId, task.getBizId(), System.currentTimeMillis() - startedMs, ex);
            task.setStatus("failed");
            task.setErrorMessage(safeMessage(ex));
            task.setNextRetryAt(LocalDateTime.now().plus(properties.getRetryDelay()));
            taskLogService.updateById(task);
        }
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
