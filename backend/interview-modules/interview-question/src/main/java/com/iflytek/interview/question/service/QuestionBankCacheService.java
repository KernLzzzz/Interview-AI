package com.iflytek.interview.question.service;

import com.iflytek.interview.question.config.QuestionCacheProperties;
import com.iflytek.interview.question.entity.Question;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Redis Hash 题库缓存层。
 *
 * <p>一个场景对应一个 Hash key，field 为 difficulty:all / difficulty:1..3。
 * Hash 让一次预热即可批量写入全部维度，并让整场景失效保持原子边界。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionBankCacheService {

    private static final String ALL = "difficulty:all";

    private final RedisTemplate<String, Object> redisTemplate;
    private final QuestionCacheProperties properties;
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder fallbacks = new LongAdder();

    public List<Question> getOrLoad(Long scenarioId, Integer difficulty, Supplier<List<Question>> loader) {
        String key = key(scenarioId);
        String field = field(difficulty);
        boolean cacheAvailable = true;
        try {
            List<Question> cached = asQuestionList(redisTemplate.opsForHash().get(key, field));
            if (cached != null) {
                hits.increment();
                return cached;
            }
            misses.increment();
        } catch (RedisConnectionFailureException ex) {
            cacheAvailable = false;
            fallbacks.increment();
            log.warn("Redis 不可用，题库查询降级到数据库: key={}", key);
        } catch (RuntimeException ex) {
            cacheAvailable = false;
            fallbacks.increment();
            log.warn("题库缓存读写异常，已降级到数据库: key={}, message={}", key, ex.getMessage());
        }
        List<Question> loaded = loader.get();
        if (cacheAvailable) {
            try {
                put(key, field, loaded);
            } catch (RuntimeException ex) {
                fallbacks.increment();
                log.warn("题库缓存回填失败，已返回数据库结果: key={}, message={}", key, ex.getMessage());
            }
        }
        return loaded;
    }

    public void replaceScenario(Long scenarioId, List<Question> questions) {
        String key = key(scenarioId);
        Map<String, Object> fields = Map.of(
                ALL, new ArrayList<>(questions),
                field(1), byDifficulty(questions, 1),
                field(2), byDifficulty(questions, 2),
                field(3), byDifficulty(questions, 3));
        try {
            redisTemplate.delete(key);
            redisTemplate.opsForHash().putAll(key, fields);
            redisTemplate.expire(key, properties.getTtl());
        } catch (RuntimeException ex) {
            fallbacks.increment();
            log.warn("题库缓存预热失败，不影响数据库读取: key={}, message={}", key, ex.getMessage());
        }
    }

    public void evict(Long scenarioId) {
        try {
            redisTemplate.delete(List.of(key(scenarioId), key(null)));
        } catch (RuntimeException ex) {
            log.warn("题库缓存失效失败，将依赖 TTL 自愈: scenarioId={}", scenarioId);
        }
    }

    public Map<String, Long> stats() {
        return Map.of("hits", hits.sum(), "misses", misses.sum(), "fallbacks", fallbacks.sum());
    }

    public String key(Long scenarioId) {
        return properties.getKeyPrefix() + ":" + (scenarioId == null ? "all" : scenarioId);
    }

    private void put(String key, String field, List<Question> questions) {
        redisTemplate.opsForHash().put(key, field, new ArrayList<>(questions));
        Duration ttl = properties.getTtl();
        redisTemplate.expire(key, ttl);
    }

    private String field(Integer difficulty) {
        return difficulty == null ? ALL : "difficulty:" + difficulty;
    }

    private List<Question> byDifficulty(List<Question> questions, int difficulty) {
        return questions.stream().filter(q -> q.getDifficulty() == difficulty).collect(Collectors.toList());
    }

    private List<Question> asQuestionList(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        List<Question> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Question question)) {
                return null;
            }
            result.add(question);
        }
        return result;
    }
}
