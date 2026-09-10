package com.iflytek.interview.interview.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
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
    private final RemoteAiEvaluationGateway remoteAiEvaluationGateway;

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
        if (remoteAiEvaluationGateway.isEnabled()) {
            JsonNode remoteReport = remoteAiEvaluationGateway.evaluate(record, answers, questions);
            int remoteScore = Math.max(0, Math.min(100, remoteReport.path("score").asInt()));
            return persist(record, remoteReport.toString(), remoteScore, "remote-ai");
        }
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
        feedback.put("version", "2.0");
        feedback.put("engine", "local-explainable");
        feedback.put("summary", buildSummary(items, overall, answers.size(), answered));
        feedback.put("items", items);
        feedback.put("dimensions", buildDimensions(items));
        feedback.put("candidateProfile", buildCandidateProfile(overall, answered, answers.size()));
        feedback.put("riskSignals", buildRiskSignals(items, answered, answers.size()));
        feedback.put("recommendations", buildRecommendations(overall));
        feedback.put("modality", Map.of(
                "mode", record.getInterviewMode() == null ? "text" : record.getInterviewMode(),
                "mediaAttached", record.getMediaFileId() != null,
                "mediaDuration", record.getMediaDuration() == null ? 0 : record.getMediaDuration()));
        String feedbackJson = writeJson(feedback);

        return persist(record, feedbackJson, overall, "local-explainable");
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
            result.put("evidence", "本题没有可分析的回答内容");
            result.put("suggestion", "先用一句话给出结论，再补充依据和实际案例");
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
        result.put("evidence", answer.length() > 80 ? "回答包含一定的解释与细节" : "回答信息量较少");
        result.put("suggestion", missed.isEmpty() ? "补充可量化的项目结果" : "优先补充遗漏的核心知识点并结合项目案例");
        return result;
    }

    private Map<String, Integer> buildDimensions(List<Map<String, Object>> items) {
        int average = items.isEmpty() ? 0 : (int) Math.round(items.stream()
                .mapToInt(item -> (int) item.get("score")).average().orElse(0) * 10);
        Map<String, Integer> dimensions = new LinkedHashMap<>();
        dimensions.put("knowledge", average);
        dimensions.put("structure", Math.min(100, average + 4));
        dimensions.put("expression", Math.max(0, average - 3));
        dimensions.put("relevance", average);
        dimensions.put("problemSolving", Math.max(0, average - 5));
        dimensions.put("communication", Math.max(0, average - 2));
        dimensions.put("growthPotential", Math.min(100, average + 6));
        return dimensions;
    }

    private Map<String, Object> buildCandidateProfile(int overall, long answered, int total) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("strengths", overall >= 70
                ? List.of("能够覆盖多数问题的核心要点", "具备一定的结构化表达意识")
                : List.of("愿意对问题给出明确回应"));
        profile.put("weaknesses", overall >= 70
                ? List.of("案例中的数据结果和个人贡献仍可更明确")
                : List.of("回答深度和关键知识点覆盖不足", "需要用具体案例证明能力"));
        profile.put("seniorityEstimate", overall >= 85 ? "高级能力表现" : overall >= 65 ? "中级能力表现" : "基础能力表现");
        profile.put("workStyle", overall >= 70 ? "偏结构化分析，建议加强结果量化" : "当前证据有限，需要在完整案例中进一步观察");
        profile.put("jobFit", String.format("基于本次模拟面试完成度 %d/%d，当前岗位匹配度为%s。",
                answered, total, overall >= 80 ? "较高" : overall >= 60 ? "中等" : "待提升"));
        return profile;
    }

    private List<String> buildRiskSignals(List<Map<String, Object>> items, long answered, int total) {
        List<String> risks = new ArrayList<>();
        if (answered < total) risks.add("存在未作答题目，能力证据不完整");
        long shortAnswers = items.stream().filter(item -> (int) item.get("score") <= 2).count();
        if (shortAnswers > 0) risks.add("部分回答过短，难以判断真实实践深度");
        if (risks.isEmpty()) risks.add("未发现明显风险，但仍需结合项目经历与人工复核");
        return risks;
    }

    private List<Map<String, String>> buildRecommendations(int overall) {
        List<Map<String, String>> recommendations = new ArrayList<>();
        recommendations.add(Map.of("priority", "high", "title", "用 STAR 结构补全案例",
                "action", "为两个核心项目分别整理情境、任务、行动和可量化结果，并控制在 2 分钟内表达。"));
        recommendations.add(Map.of("priority", overall < 70 ? "high" : "medium", "title", "补齐岗位知识证据",
                "action", "根据逐题遗漏关键词建立复习清单，每个知识点准备原理、取舍和落地案例。"));
        recommendations.add(Map.of("priority", "medium", "title", "进行限时复述训练",
                "action", "每天录制 3 道题，回听并删掉重复表述，让结论出现在回答前 20 秒。"));
        return recommendations;
    }

    private String persist(InterviewRecord record, String feedbackJson, int score, String engine) {
        record.setScore(BigDecimal.valueOf(score));
        record.setAiFeedback(feedbackJson);
        interviewRecordMapper.updateById(record);
        log.info("AI 评测完成: recordId={}, engine={}, score={}", record.getId(), engine, score);
        return feedbackJson;
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
