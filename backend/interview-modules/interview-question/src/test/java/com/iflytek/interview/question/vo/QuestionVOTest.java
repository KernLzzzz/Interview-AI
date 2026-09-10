package com.iflytek.interview.question.vo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionVOTest {

    @Test
    void candidateViewCannotSerializeAnswerFields() throws Exception {
        QuestionVO vo = new QuestionVO();
        vo.setId(1L);
        vo.setContent("如何保证缓存一致性？");

        String json = new ObjectMapper().writeValueAsString(vo);

        assertThat(json).doesNotContain("expectedAnswer", "keywords");
    }
}
