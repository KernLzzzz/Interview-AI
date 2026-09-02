package com.iflytek.interview.job.task;

import com.iflytek.interview.interview.entity.TaskLog;
import com.iflytek.interview.interview.service.TaskLogService;
import com.iflytek.interview.interview.task.ReportGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 面试平台定时任务。
 * 职责边界：job 只负责"调度"（找失败任务、触发重试），业务执行在 interview 模块的 ReportGenerator。
 */
@Slf4j
@Component
public class InterviewScheduledTasks {

    @Autowired
    private TaskLogService taskLogService;

    @Autowired
    private ReportGenerator reportGenerator;

    /**
     * 每天早上 8 点：发送面试提醒（本讲先打日志模拟，短信/邮件对接后续）
     */
    @Scheduled(cron = "0 0 8 * * ?")
    public void sendInterviewReminder() {
        log.info("【定时】发送面试提醒开始");
        // 1. 查当天有面试的用户（第12讲接 RecordService）
        // 2. 逐条发送提醒
        // 3. 记录发送日志
        log.info("【定时】发送面试提醒结束");
    }

    /**
     * 每天凌晨 2 点：清理 3 个月前的已完成记录
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanExpiredRecords() {
        log.info("【定时】清理过期记录开始");
        LocalDateTime before = LocalDateTime.now().minusMonths(3);
        // int rows = recordService.deleteExpired(before);   // 第12讲接上
        log.info("【定时】清理过期记录结束");
    }

    /**
     * 每周一 9 点：统计上周面试数据（出报表）
     */
    @Scheduled(cron = "0 0 9 ? * MON")
    public void generateWeeklyReport() {
        log.info("【定时】生成上周统计报表");
        // 统计上周总数 / 完成数 / 平均分，落库或发送
    }

    /**
     * 每 5 分钟：扫描失败任务并重试（最多重试 3 次，防死循环）
     * 定时任务 + 异步任务的配合：定时只负责"找出来、标记好"，重活交回异步线程池
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void retryFailedTasks() {
        // 1. 找出"失败了且还没到重试上限"的任务
        List<TaskLog> failed = taskLogService.lambdaQuery()
                .eq(TaskLog::getStatus, "failed")
                .lt(TaskLog::getRetryCount, 3)
                .list();
        if (failed.isEmpty()) {
            return;
        }
        // 2. 逐个标记"重试中"，再重新触发对应异步任务（失败原因若复现会被重新记录）
        for (TaskLog task : failed) {
            task.setRetryCount(task.getRetryCount() + 1);
            task.setStatus("running");
            taskLogService.updateById(task);
            reportGenerator.generateReportWithLog(task.getBizId());
        }
        log.info("【定时】重试 {} 个失败任务", failed.size());
    }
}
