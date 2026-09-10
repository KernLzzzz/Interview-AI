package com.iflytek.interview.interview.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.interview.interview.config.AiEvaluationProperties;
import com.iflytek.interview.interview.dto.InterviewAnswer;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.question.entity.Question;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteAiEvaluationGatewayTest {

    @Test
    void enabledGatewayDoesNotSilentlyFallbackWhenCredentialsAreMissing() {
        AiEvaluationProperties properties = new AiEvaluationProperties();
        properties.setEnabled(true);
        RemoteAiEvaluationGateway gateway = new RemoteAiEvaluationGateway(properties, new ObjectMapper());

        assertThatThrownBy(() -> gateway.evaluate(new InterviewRecord(), List.of(), Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API Key");
    }

    @Test
    void callsCompatibleModelAndNormalizesStructuredReport() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String modelReport = """
                {"score":82,"summary":"候选人基础扎实","dimensions":{"knowledge":84},
                "candidateProfile":{"strengths":["原理清楚"],"weaknesses":["案例不足"]},
                "riskSignals":[],"recommendations":[{"priority":"high","title":"补案例","action":"准备STAR案例"}],
                "items":[{"questionId":1,"score":8,"feedback":"回答准确","evidence":"提到TTL","suggestion":"补充指标"}]}
                """;
        String response = mapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", modelReport)))));
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/elin/evaluate", exchange -> {
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            AiEvaluationProperties properties = new AiEvaluationProperties();
            properties.setEnabled(true);
            properties.setApiKey("test-key");
            properties.setModel("test-model");
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setEndpoint("/elin/evaluate");
            RemoteAiEvaluationGateway gateway = new RemoteAiEvaluationGateway(properties, mapper);
            InterviewRecord record = new InterviewRecord();
            record.setScenarioId(1L);
            record.setInterviewMode("voice");
            record.setMediaFileId(9L);
            Question question = new Question();
            question.setId(1L);
            question.setExpectedAnswer("Redis TTL");

            JsonNode report = gateway.evaluate(record,
                    List.of(new InterviewAnswer(1L, "如何设计缓存？", "使用 Redis Hash 和 TTL")),
                    Map.of(1L, question));

            assertThat(report.path("score").asInt()).isEqualTo(82);
            assertThat(report.path("engine").asText()).isEqualTo("remote-ai");
            assertThat(report.path("model").asText()).isEqualTo("test-model");
            assertThat(report.path("modality").path("mediaAttached").asBoolean()).isTrue();
        } finally {
            server.stop(0);
        }
    }
}
