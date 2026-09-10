package com.iflytek.interview.question.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.iflytek.interview.question.entity.Question;

import java.util.List;

public interface QuestionService extends IService<Question> {

    /** 题目列表：按场景、难度筛选（缓存） */
    List<Question> listQuestions(Long scenarioId, Integer difficulty);

    /** 新增题目（写后清题目缓存） */
    Question saveQuestion(Question question);

    /** 修改题目（写后清题目缓存） */
    Question updateQuestion(Question question);

    /** 删除题目（写后清题目缓存） */
    void deleteQuestion(Long id);

    /** 从指定场景随机抽 count 道题（仅上架状态） */
    List<Question> getRandomQuestions(Long scenarioId, int count);

    /** 按难度比例 3:5:2（简单:中等:困难）随机抽 count 道题，最后打乱顺序 */
    List<Question> getRandomQuestionsByRatio(Long scenarioId, int count);

    /** 预热全局列表与前 limit 个热点场景，返回预热场景数。 */
    int warmupHotQuestionBanks(int limit);
}
