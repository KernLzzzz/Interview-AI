package com.iflytek.interview.interview.dto;

import com.iflytek.interview.question.entity.Question;

/** 候选人可见的不可变抽题快照，不包含答案与评分关键词。 */
public record QuestionSnapshot(Long id, String content, String type, Integer difficulty) {
    public static QuestionSnapshot from(Question question) {
        return new QuestionSnapshot(question.getId(), question.getContent(),
                question.getType(), question.getDifficulty());
    }
}
