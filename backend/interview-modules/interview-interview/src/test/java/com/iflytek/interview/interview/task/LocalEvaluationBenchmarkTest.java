package com.iflytek.interview.interview.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.mapper.InterviewRecordMapper;
import com.iflytek.interview.question.entity.Question;
import com.iflytek.interview.question.mapper.QuestionMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalEvaluationBenchmarkTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void goldenCasesStayWithinExpectedScoreBands() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/evaluation-benchmark.json")) {
            List<Map<String, Object>> cases = objectMapper.readValue(input, new TypeReference<>() { });
            for (Map<String, Object> benchmark : cases) {
                int score = evaluate(String.valueOf(benchmark.get("answer")), String.valueOf(benchmark.get("keywords")));
                assertThat(score).as(String.valueOf(benchmark.get("name")))
                        .isBetween((Integer) benchmark.get("minScore"), (Integer) benchmark.get("maxScore"));
            }
        }
    }

    private int evaluate(String answer, String keywords) throws Exception {
        InterviewRecordMapper recordMapper = mock(InterviewRecordMapper.class);
        QuestionMapper questionMapper = mock(QuestionMapper.class);
        RemoteAiEvaluationGateway gateway = mock(RemoteAiEvaluationGateway.class);
        InterviewRecord record = new InterviewRecord();
        record.setId(1L);
        record.setAnswerData(objectMapper.writeValueAsString(List.of(Map.of(
                "id", 1, "content", "如何设计高并发缓存？", "answer", answer))));
        Question question = new Question();
        question.setId(1L);
        question.setKeywords(keywords);
        when(recordMapper.selectById(1L)).thenReturn(record);
        when(questionMapper.selectBatchIds(anyList())).thenReturn(List.of(question));
        when(gateway.isEnabled()).thenReturn(false);

        String report = new ReportGenerator(recordMapper, questionMapper, objectMapper, gateway).generate(1L);
        JsonNode json = objectMapper.readTree(report);
        return json.path("score").asInt();
    }
}
