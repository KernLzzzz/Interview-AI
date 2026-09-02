package com.iflytek.interview.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.interview.question.entity.Question;
import com.iflytek.interview.question.mapper.QuestionMapper;
import com.iflytek.interview.question.service.QuestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {

    // ===== 缓存约定：questions 缓存空间，任何写操作 allEntries 全清（题目量不大，全清最稳）=====

    /** 题目列表：命中缓存直接返回；key 带筛选参数（scenarioId/difficulty），null 也参与拼 key */
    @Override
    @Cacheable(value = "questions", key = "'list:' + #scenarioId + ':' + #difficulty")
    public List<Question> listQuestions(Long scenarioId, Integer difficulty) {
        log.info("【缓存未命中】查数据库：题目列表 scenarioId={}, difficulty={}", scenarioId, difficulty);
        return this.lambdaQuery()
                .eq(scenarioId != null, Question::getScenarioId, scenarioId)
                .eq(difficulty != null, Question::getDifficulty, difficulty)
                .eq(Question::getStatus, 1)
                .orderByAsc(Question::getSortOrder)
                .list();
    }

    @Override
    @CacheEvict(value = "questions", allEntries = true)
    public Question saveQuestion(Question question) {
        this.save(question);
        return question;
    }

    @Override
    @CacheEvict(value = "questions", allEntries = true)
    public Question updateQuestion(Question question) {
        this.updateById(question);
        return question;
    }

    @Override
    @CacheEvict(value = "questions", allEntries = true)
    public void deleteQuestion(Long id) {
        this.removeById(id);
    }

    /**
     * 从指定场景随机抽 count 道题（MySQL RAND()）
     * 注意：排序完全交给 last() 拼接的 ORDER BY RAND()，不能再写 orderBy...（两个 ORDER BY 会语法报错）
     */
    @Override
    public List<Question> getRandomQuestions(Long scenarioId, int count) {
        return baseMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getScenarioId, scenarioId)
                .eq(Question::getStatus, 1)
                .last("ORDER BY RAND() LIMIT " + count));
    }

    /**
     * 按难度比例 3:5:2（简单1:中等2:困难3）随机抽题。
     * 各难度先按配额随机抽，某难度题不足时把缺口顺延给其他难度补齐，最后整体打乱顺序。
     */
    @Override
    public List<Question> getRandomQuestionsByRatio(Long scenarioId, int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }
        // 3:5:2 → 配额四舍五入（count=10 时 简单3/中等5/困难2）
        int easy = Math.round(count * 0.3f);
        int medium = Math.round(count * 0.5f);
        int hard = count - easy - medium;   // 余数全部给困难，保证总数等于 count

        List<Question> result = new ArrayList<>();
        result.addAll(randomByDifficulty(scenarioId, 1, easy));
        result.addAll(randomByDifficulty(scenarioId, 2, medium));
        result.addAll(randomByDifficulty(scenarioId, 3, hard));

        // 某难度题不足时，用该场景其他题补齐缺口（排除已抽中的）
        if (result.size() < count) {
            List<Question> rest = baseMapper.selectList(new LambdaQueryWrapper<Question>()
                    .eq(Question::getScenarioId, scenarioId)
                    .eq(Question::getStatus, 1)
                    .notIn(!result.isEmpty(), Question::getId,
                            result.stream().map(Question::getId).toList())
                    .last("ORDER BY RAND() LIMIT " + (count - result.size())));
            result.addAll(rest);
        }

        Collections.shuffle(result);   // 打乱：不让候选人看出难度分布
        return result;
    }

    /** 抽指定难度的 limit 道题（随机排序） */
    private List<Question> randomByDifficulty(Long scenarioId, int difficulty, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        return baseMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getScenarioId, scenarioId)
                .eq(Question::getStatus, 1)
                .eq(Question::getDifficulty, difficulty)
                .last("ORDER BY RAND() LIMIT " + limit));
    }
}
