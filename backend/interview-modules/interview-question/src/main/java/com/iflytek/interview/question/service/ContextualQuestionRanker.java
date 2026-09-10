package com.iflytek.interview.question.service;

import com.iflytek.interview.question.entity.Question;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** 用岗位上下文对缓存题库做轻量、可解释的相关性排序。 */
@Component
public class ContextualQuestionRanker {

    public List<Question> rank(List<Question> questions, String context) {
        if (!StringUtils.hasText(context)) return new ArrayList<>(questions);
        String normalized = context.toLowerCase();
        return questions.stream()
                .sorted(Comparator.<Question>comparingInt(question -> score(question, normalized)).reversed()
                        .thenComparingLong(question -> stableTieBreaker(question, normalized)))
                .toList();
    }

    int score(Question question, String context) {
        int score = 0;
        if (StringUtils.hasText(question.getKeywords())) {
            score += (int) Arrays.stream(question.getKeywords().split("[,，、;；\\s]+"))
                    .map(String::trim).filter(term -> term.length() >= 2)
                    .filter(term -> context.contains(term.toLowerCase())).count() * 10;
        }
        if (StringUtils.hasText(question.getType()) && context.contains(question.getType().toLowerCase())) score += 3;
        return score;
    }

    private long stableTieBreaker(Question question, String context) {
        return Math.abs((String.valueOf(question.getId()) + ':' + context.hashCode()).hashCode());
    }
}
