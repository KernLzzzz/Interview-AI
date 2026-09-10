package com.iflytek.interview.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.interview.question.entity.Question;
import com.iflytek.interview.question.mapper.QuestionMapper;
import com.iflytek.interview.question.service.QuestionService;
import lombok.extern.slf4j.Slf4j;
import com.iflytek.interview.question.service.QuestionBankCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {

    @Autowired
    private QuestionBankCacheService questionBankCache;

    /** 题目列表：命中缓存直接返回；key 带筛选参数（scenarioId/difficulty），null 也参与拼 key */
    @Override
    public List<Question> listQuestions(Long scenarioId, Integer difficulty) {
        return questionBankCache.getOrLoad(scenarioId, difficulty,
                () -> loadActiveQuestions(scenarioId, difficulty));
    }

    @Override
    public Question saveQuestion(Question question) {
        this.save(question);
        questionBankCache.evict(question.getScenarioId());
        return question;
    }

    @Override
    public Question updateQuestion(Question question) {
        Question before = this.getById(question.getId());
        this.updateById(question);
        if (before != null) {
            questionBankCache.evict(before.getScenarioId());
        }
        if (before == null || !before.getScenarioId().equals(question.getScenarioId())) {
            questionBankCache.evict(question.getScenarioId());
        }
        return question;
    }

    @Override
    public void deleteQuestion(Long id) {
        Question before = this.getById(id);
        this.removeById(id);
        if (before != null) {
            questionBankCache.evict(before.getScenarioId());
        }
    }

    /**
     * 从指定场景随机抽 count 道题（MySQL RAND()）
     * 注意：排序完全交给 last() 拼接的 ORDER BY RAND()，不能再写 orderBy...（两个 ORDER BY 会语法报错）
     */
    @Override
    public List<Question> getRandomQuestions(Long scenarioId, int count) {
        List<Question> candidates = new ArrayList<>(listQuestions(scenarioId, null));
        Collections.shuffle(candidates);
        return candidates.subList(0, Math.min(count, candidates.size()));
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

        // 随机抽题只读取一个 Hash field；冷启动也只回源一次数据库。
        List<Question> bank = new ArrayList<>(listQuestions(scenarioId, null));
        List<Question> result = new ArrayList<>();
        result.addAll(randomByDifficulty(bank, 1, easy));
        result.addAll(randomByDifficulty(bank, 2, medium));
        result.addAll(randomByDifficulty(bank, 3, hard));

        // 某难度题不足时，用该场景其他题补齐缺口（排除已抽中的）
        if (result.size() < count) {
            List<Long> selectedIds = result.stream().map(Question::getId).toList();
            List<Question> rest = new ArrayList<>(bank.stream()
                    .filter(q -> !selectedIds.contains(q.getId())).toList());
            Collections.shuffle(rest);
            rest = rest.subList(0, Math.min(count - result.size(), rest.size()));
            result.addAll(rest);
        }

        Collections.shuffle(result);   // 打乱：不让候选人看出难度分布
        return result;
    }

    /** 抽指定难度的 limit 道题（随机排序） */
    private List<Question> randomByDifficulty(List<Question> bank, int difficulty, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        List<Question> candidates = new ArrayList<>(bank.stream()
                .filter(question -> question.getDifficulty() == difficulty).toList());
        Collections.shuffle(candidates);
        return candidates.subList(0, Math.min(limit, candidates.size()));
    }

    @Override
    public int warmupHotQuestionBanks(int limit) {
        List<Question> all = loadActiveQuestions(null, null);
        questionBankCache.replaceScenario(null, all);
        List<Long> scenarioIds = all.stream().map(Question::getScenarioId).distinct()
                .limit(Math.max(0, limit)).toList();
        for (Long scenarioId : scenarioIds) {
            questionBankCache.replaceScenario(scenarioId,
                    all.stream().filter(q -> scenarioId.equals(q.getScenarioId())).toList());
        }
        return scenarioIds.size();
    }

    private List<Question> loadActiveQuestions(Long scenarioId, Integer difficulty) {
        log.info("题库缓存未命中，查询数据库: scenarioId={}, difficulty={}", scenarioId, difficulty);
        return baseMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(scenarioId != null, Question::getScenarioId, scenarioId)
                .eq(difficulty != null, Question::getDifficulty, difficulty)
                .eq(Question::getStatus, 1)
                .orderByAsc(Question::getSortOrder));
    }
}
