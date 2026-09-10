package com.iflytek.interview.interview.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.interview.question.entity.Question;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionSnapshotTest {

    @Test
    void snapshotIsStructuredAndContainsNoAnswerMaterial() throws Exception {
        Question source = new Question();
        source.setId(88L);
        source.setContent("解释线程池拒绝策略");
        source.setType("technical");
        source.setDifficulty(2);
        source.setExpectedAnswer("CallerRunsPolicy");
        source.setKeywords("线程池,拒绝策略");

        String json = new ObjectMapper().writeValueAsString(QuestionSnapshot.from(source));

        assertThat(json).contains("id\":88", "content", "type", "difficulty")
                .doesNotContain("expectedAnswer", "keywords", "CallerRunsPolicy");
    }
}
