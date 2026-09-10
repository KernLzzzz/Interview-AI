package com.iflytek.interview.interview.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.interview.interview.dto.InterviewAnswer;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.mapper.InterviewRecordMapper;
import com.iflytek.interview.question.entity.Question;
import com.iflytek.interview.question.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 可替换的评测内核。当前使用答案完整度、关键词覆盖与表达结构进行本地评分，
 * 对外始终输出稳定的结构化 JSON，后续接入远程大模型不需要改动任务接口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportGenerator {

    private final InterviewRecordMapper interviewRecordMapper;
    private final QuestionMapper questionMapper;
    private final ObjectMapper objectMapper;

    /** 同步执行一次评测；异步边界由 EvaluationWorker 统一控制。 */
    public String generate(Long recordId) {
        InterviewRecord record = interviewRecordMapper.selectById(recordId);
        if (record == null) {
            throw new IllegalArgumentException("面试记录不存在: " + recordId);
        }
        if (record.getScore() != null && StringUtils.hasText(record.getAiFeedback())) {
            return record.getAiFeedback();
        }

        List<InterviewAnswer> answers = parseAnswers(record);
        Map<Long, Question> questions = loadQuestions(answers);
        List<Map<String, Object>> items = new ArrayList<>();
        for (InterviewAnswer item : answers) {
            Question question = questions.get(item.id());
            List<String> keywords = splitKeywords(question == null ? null : question.getKeywords());
            Map<String, Object> analysis = analyzeQuestion(item.answer(), keywords);
            analysis.put("questionId", item.id());
            items.add(analysis);
        }

        int overall = items.isEmpty() ? 0 : (int) Math.round(items.stream()
                .mapToInt(item -> (int) item.get("score")).average().orElse(0) * 10);
        overall = Math.max(0, Math.min(100, overall));
        long answered = items.stream().filter(item -> (int) item.get("score") > 0).count();

        Map<String, Object> feedback = new LinkedHashMap<>();
        feedback.put("version", "1.0");
        feedback.put("summary", buildSummary(items, overall, answers.size(), answered));
        feedback.put("items", items);
        feedback.put("dimensions", buildDimensions(items));
        String feedbackJson = writeJson(feedback);

        record.setScore(BigDecimal.valueOf(overall));
        record.setAiFeedback(feedbackJson);
        interviewRecordMapper.updateById(record);
        log.info("AI 评测完成: recordId={}, score={}, answered={}/{}",
                recordId, overall, answered, answers.size());
        return feedbackJson;
    }

    private List<InterviewAnswer> parseAnswers(InterviewRecord record) {
        if (!StringUtils.hasText(record.getAnswerData())) {
            return List.of();
        }
        try {
            return objectMapper.readValue(record.getAnswerData(), new TypeReference<>() { });
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("答题数据不是有效 JSON", ex);
        }
    }

    private Map<Long, Question> loadQuestions(List<InterviewAnswer> answers) {
        List<Long> ids = answers.stream().map(InterviewAnswer::id).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return questionMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
    }

    private List<String> splitKeywords(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split("[,，、;；\\s]+"))
                .filter(StringUtils::hasText).distinct().toList();
    }

    private Map<String, Object> analyzeQuestion(String rawAnswer, List<String> keywords) {
        String answer = rawAnswer == null ? "" : rawAnswer.trim();
        Map<String, Object> result = new LinkedHashMap<>();
        if (answer.isEmpty()) {
            result.put("score", 0);
            result.put("matchedKeywords", List.of());
            result.put("feedback", "未作答，建议先给出结论，再说明依据与实践例子。");
            return result;
        }

        int lengthScore = answer.length() >= 200 ? 8 : answer.length() >= 100 ? 6
                : answer.length() >= 50 ? 4 : answer.length() >= 20 ? 2 : 1;
        String lower = answer.toLowerCase();
        List<String> matched = keywords.stream()
                .filter(keyword -> lower.contains(keyword.toLowerCase())).toList();
        int keywordScore = keywords.isEmpty() ? 0 : Math.min(6, matched.size());
        int structureScore = answer.contains("\n") || answer.matches(".*[0-9].*")
                || answer.contains("、") ? 1 : 0;
        int score = Math.min(10, lengthScore + keywordScore + structureScore);

        List<String> missed = keywords.stream().filter(keyword -> !matched.contains(keyword)).toList();
        String feedback;
        if (keywords.isEmpty()) {
            feedback = answer.length() < 50 ? "观点已表达，建议补充具体场景、行动与结果。"
                    : "回答较完整，可以进一步量化结果与复盘思路。";
        } else if (missed.isEmpty()) {
            feedback = "核心要点覆盖完整，建议用实际案例强化说服力。";
        } else {
            feedback = "已覆盖「" + String.join("、", matched) + "」，建议补充「"
                    + String.join("、", missed) + "」。";
        }
        result.put("score", score);
        result.put("matchedKeywords", matched);
        result.put("feedback", feedback);
        return result;
    }

    private Map<String, Integer> buildDimensions(List<Map<String, Object>> items) {
        int average = items.isEmpty() ? 0 : (int) Math.round(items.stream()
                .mapToInt(item -> (int) item.get("score")).average().orElse(0) * 10);
        Map<String, Integer> dimensions = new LinkedHashMap<>();
        dimensions.put("knowledge", average);
        dimensions.put("structure", Math.min(100, average + 4));
        dimensions.put("expression", Math.max(0, average - 3));
        return dimensions;
    }

    private String buildSummary(List<Map<String, Object>> items, int overall, int total, long answered) {
        if (answered == 0) {
            return "本次面试没有有效作答。建议先完成每道题的核心观点，再补充依据和案例。";
        }
        Map<String, Object> best = items.stream().filter(item -> (int) item.get("score") > 0)
                .max(Comparator.comparingInt(item -> (int) item.get("score"))).orElseThrow();
        return String.format("本次共 %d 题，完成 %d 题，综合评分 %d 分。最佳单题 %s/10，继续加强薄弱知识点并用 STAR 结构组织回答。",
                total, answered, overall, best.get("score"));
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("评测结果序列化失败", ex);
        }
    }
}
