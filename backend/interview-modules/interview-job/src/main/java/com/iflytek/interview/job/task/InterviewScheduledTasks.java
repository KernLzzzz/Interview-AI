package com.iflytek.interview.job.task;

import com.iflytek.interview.file.service.OrphanMediaCleaner;
import com.iflytek.interview.interview.service.EvaluationTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 面试平台定时任务。
 * 职责边界：job 只负责"调度"（找失败任务、触发重试），业务执行在 interview 模块的 ReportGenerator。
 */
@Slf4j
@Component
public class InterviewScheduledTasks {

    @Autowired
    private EvaluationTaskService evaluationTaskService;

    @Autowired
    private OrphanMediaCleaner orphanMediaCleaner;

    /** 孤儿媒体保留时长：超过该小时数仍未被任何面试记录引用，才判定为可回收 */
    @Value("${app.file.orphan-ttl-hours:24}")
    private int orphanTtlHours;

    /** 单轮最多清理条数，避免一次删除过多拖长任务 */
    @Value("${app.file.orphan-clean-batch:200}")
    private int orphanCleanBatch;

    /**
     * 每天早上 8 点：发送面试提醒（本讲先打日志模拟，短信/邮件对接后续）
     */
    @Scheduled(cron = "0 0 8 * * ?")
    public void sendInterviewReminder() {
        log.info("【定时】发送面试提醒开始");
        // 1. 查询当天有面试的用户
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
        // int rows = recordService.deleteExpired(before);
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
        int retried = evaluationTaskService.retryDueTasks();
        if (retried > 0) {
            log.info("【定时】已重新投递 {} 个 AI 评测任务", retried);
        }
    }

    /**
     * 每小时第 15 分：回收孤儿面试媒体。
     * 上传与落库是两步操作，中间失败、放弃或被取消，都会在对象存储里留下无人引用的录制文件，
     * 且这些文件既不会被回看也不会被评测。这里按 TTL 兜底回收，阻止存储持续泄漏。
     * cron、保留时长、单轮上限均可在配置中覆盖。
     */
    @Scheduled(cron = "${app.file.orphan-clean-cron:0 15 * * * ?}")
    public void cleanOrphanMediaFiles() {
        int cleaned = orphanMediaCleaner.clean(Duration.ofHours(orphanTtlHours), orphanCleanBatch);
        if (cleaned > 0) {
            log.info("【定时】已清理 {} 个孤儿面试媒体文件", cleaned);
        }
    }
}
