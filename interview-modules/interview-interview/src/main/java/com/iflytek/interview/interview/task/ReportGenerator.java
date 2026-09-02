package com.iflytek.interview.interview.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.entity.TaskLog;
import com.iflytek.interview.interview.mapper.InterviewRecordMapper;
import com.iflytek.interview.interview.service.TaskLogService;
import com.iflytek.interview.question.entity.Question;
import com.iflytek.interview.question.mapper.QuestionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 评测报告生成器：提交面试后异步逐题点评，写回 score + aiFeedback（结构化 JSON）。
 * 说明：本版本按「答案长度 + 关键词命中 + 结构」启发式逐题评分，无外部 LLM 依赖；
 * 后续可替换 doGenerate 内部为真实 AI 调用，输出结构保持不变。
 */
@Slf4j
@Component
public class ReportGenerator {

    @Autowired
    private TaskLogService taskLogService;

    @Autowired
    private InterviewRecordMapper interviewRecordMapper;

    @Autowired
    private QuestionMapper questionMapper;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 异步生成评测报告（无日志版，演示用）：外部调用立即返回，真正工作在后台线程执行
     */
    @Async
    public void generateReport(Long recordId) {
        log.info("【异步】开始生成报告: recordId={}, 线程={}",
                recordId, Thread.currentThread().getName());
        try {
            // 模拟"调 AI + 算分 + 生成报告"耗时（后续替换为真实调用）
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("【异步】报告生成完成: recordId={}", recordId);
    }

    /**
     * 带任务日志的异步评测（工程版）：先落 task_log(running)，成功改 success，失败改 failed + 原因。
     * 失败任务由 interview-job 的定时重试扫描（status=failed 且 retryCount<3）。
     */
    @Async
    public void generateReportWithLog(Long recordId) {
        TaskLog taskLog = new TaskLog();
        taskLog.setTaskType("report_generate");
        taskLog.setBizId(recordId);
        taskLog.setStatus("running");
        taskLog.setRetryCount(0);
        taskLogService.save(taskLog);

        try {
            log.info("【异步】开始生成报告(task_log={}): recordId={}, 线程={}",
                    taskLog.getId(), recordId, Thread.currentThread().getName());
            doGenerate(recordId);
            taskLog.setStatus("success");
        } catch (Exception e) {
            log.error("【异步】报告生成失败: recordId={}", recordId, e);
            taskLog.setStatus("failed");
            taskLog.setErrorMessage(e.getMessage());
        }
        taskLogService.updateById(taskLog);
    }

    /**
     * 逐题点评 + 综合评分，写回 score / aiFeedback(JSON)
     */
    private void doGenerate(Long recordId) throws InterruptedException {
        Thread.sleep(5000);

        InterviewRecord record = interviewRecordMapper.selectById(recordId);
        // 幂等：记录不存在或已评分则不再写
        if (record == null || record.getScore() != null) {
            return;
        }

        // 解析 answerData：[{id, content, answer}]
        List<Map<String, Object>> answers = new ArrayList<>();
        if (StringUtils.hasText(record.getAnswerData())) {
            try {
                answers = objectMapper.readValue(record.getAnswerData(),
                        new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                log.warn("answerData 解析失败，按未作答评分: recordId={}", recordId);
            }
        }

        // 逐题点评
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> item : answers) {
            Object idObj = item.get("id");
            Object answerObj = item.get("answer");
            String answer = answerObj == null ? "" : answerObj.toString().trim();

            // 查该题关键词
            List<String> keywords = new ArrayList<>();
            if (idObj != null) {
                try {
                    Question q = questionMapper.selectById(((Number) idObj).longValue());
                    if (q != null && StringUtils.hasText(q.getKeywords())) {
                        keywords = Arrays.stream(q.getKeywords().split("[,，、;；\\s]+"))
                                .filter(StringUtils::hasText)
                                .collect(Collectors.toList());
                    }
                } catch (Exception e) {
                    log.warn("查题目关键词失败: questionId={}", idObj);
                }
            }

            Map<String, Object> analysis = analyzeQuestion(answer, keywords);
            analysis.put("questionId", idObj);
            items.add(analysis);
        }

        // 综合分 = 每题平均 × 10
        int overall = items.isEmpty() ? 0
                : (int) Math.round(items.stream()
                        .mapToInt(i -> (int) i.get("score")).average().orElse(0) * 10);
        overall = Math.max(0, Math.min(100, overall));

        long answered = items.stream().filter(i -> (int) i.get("score") > 0).count();
        String summary = buildSummary(items, overall, answers.size(), answered);

        // aiFeedback 结构化 JSON
        Map<String, Object> feedback = new LinkedHashMap<>();
        feedback.put("summary", summary);
        feedback.put("items", items);
        String feedbackJson;
        try {
            feedbackJson = objectMapper.writeValueAsString(feedback);
        } catch (JsonProcessingException e) {
            feedbackJson = summary;
        }

        record.setScore(new BigDecimal(overall));
        record.setAiFeedback(feedbackJson);
        interviewRecordMapper.updateById(record);
        log.info("AI 评测完成: recordId={}, score={}, 作答 {}/{}", recordId, overall, answered, answers.size());
    }

    /** 单题分析：按长度 + 关键词命中 + 结构给 0-10 分和评语 */
    private Map<String, Object> analyzeQuestion(String answer, List<String> keywords) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (answer.isEmpty()) {
            result.put("score", 0);
            result.put("feedback", "未作答，建议尝试作答以获取反馈。");
            return result;
        }

        // 长度分 1-8
        int len = answer.length();
        int lenScore = len >= 200 ? 8 : len >= 100 ? 6 : len >= 50 ? 4 : len >= 20 ? 2 : 1;
        // 关键词命中分 0-6
        String lower = answer.toLowerCase();
        List<String> matched = keywords.stream()
                .filter(k -> lower.contains(k.toLowerCase())).collect(Collectors.toList());
        int kwScore = keywords.isEmpty() ? 0 : Math.min(6, matched.size());
        // 结构分 0-1（含换行/数字/顿号）
        int struct = (answer.contains("\n") || answer.matches(".*[0-9].*") || answer.contains("、")) ? 1 : 0;

        int score = Math.min(10, lenScore + kwScore + struct);

        // 评语
        StringBuilder fb = new StringBuilder();
        if (keywords.isEmpty()) {
            if (len < 30) fb.append("回答较简略，建议围绕要点展开并举例说明。");
            else if (len < 100) fb.append("回答基本完整，可再补充具体例子。");
            else fb.append("回答较充实，表述清晰。");
        } else {
            List<String> missed = keywords.stream()
                    .filter(k -> !matched.contains(k)).collect(Collectors.toList());
            if (!matched.isEmpty()) fb.append("覆盖要点：").append(String.join("、", matched)).append("。");
            if (!missed.isEmpty()) fb.append("建议补充：").append(String.join("、", missed)).append("。");
            if (missed.isEmpty()) fb.append("要点覆盖较完整。");
        }
        if (len < 20) fb.insert(0, "回答过于简略。");

        result.put("score", score);
        result.put("feedback", fb.toString());
        return result;
    }

    /** 汇总评语 */
    private String buildSummary(List<Map<String, Object>> items, int overall, int total, long answered) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("本次面试共 %d 题，作答 %d 题，综合评分 %d 分。", total, answered, overall));

        Map<String, Object> best = items.stream().filter(i -> (int) i.get("score") > 0)
                .max(Comparator.comparingInt(i -> (int) i.get("score"))).orElse(null);
        Map<String, Object> worst = items.stream().filter(i -> (int) i.get("score") > 0)
                .min(Comparator.comparingInt(i -> (int) i.get("score"))).orElse(null);
        if (best != null) {
            sb.append("你回答最充分的一道题得分 ").append(best.get("score")).append("/10。");
            if (worst != null && (int) worst.get("score") < (int) best.get("score")) {
                sb.append("相对薄弱的一道题得分 ").append(worst.get("score"))
                        .append("/10，建议针对其要点重点复习。");
            }
        }
        if (answered == 0) {
            sb.append("请认真作答每一道题，才能获得更有价值的反馈。");
        }
        return sb.toString();
    }

    /**
     * 带返回值的异步：既能"发出去不管"，也能在需要时拿到结果
     * （future.get(10, TimeUnit.SECONDS) 最多等 10 秒，防无限阻塞）
     */
    @Async
    public CompletableFuture<String> generateReportAsync(Long recordId) {
        log.info("【异步】开始生成报告(带返回值): recordId={}, 线程={}",
                recordId, Thread.currentThread().getName());
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("【异步】报告生成完成(带返回值): recordId={}", recordId);
        return CompletableFuture.completedFuture("report-" + recordId);
    }
}
