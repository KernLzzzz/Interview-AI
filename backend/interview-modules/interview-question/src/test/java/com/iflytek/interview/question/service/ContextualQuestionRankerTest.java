package com.iflytek.interview.question.service;

import com.iflytek.interview.question.entity.Question;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextualQuestionRankerTest {

    @Test
    void ranksQuestionsWhoseKeywordsMatchJobDescriptionFirst() {
        Question redis = question(1L, "Redis 缓存设计", "Redis,缓存,高并发");
        Question css = question(2L, "CSS 布局", "CSS,Flexbox");

        List<Question> ranked = new ContextualQuestionRanker().rank(
                List.of(css, redis), "岗位要求熟悉 Redis、缓存一致性和高并发系统设计");

        assertThat(ranked).extracting(Question::getId).containsExactly(1L, 2L);
    }

    private Question question(Long id, String content, String keywords) {
        Question question = new Question();
        question.setId(id);
        question.setContent(content);
        question.setKeywords(keywords);
        question.setDifficulty(2);
        return question;
    }
}
