package com.iflytek.interview.question.service;

import com.iflytek.interview.question.config.QuestionCacheProperties;
import com.iflytek.interview.question.entity.Question;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionBankCacheServiceTest {

    private RedisTemplate<String, Object> redisTemplate;
    private HashOperations<String, Object, Object> hashOperations;
    private QuestionBankCacheService cache;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        cache = new QuestionBankCacheService(redisTemplate, new QuestionCacheProperties());
    }

    @Test
    void keyFollowsBusinessModuleDimensionConvention() {
        assertThat(cache.key(42L)).isEqualTo("interview:question-bank:scenario:42");
        assertThat(cache.key(null)).isEqualTo("interview:question-bank:scenario:all");
    }

    @Test
    void cacheHitDoesNotQueryDatabase() {
        Question question = new Question();
        question.setId(1L);
        question.setDifficulty(2);
        when(hashOperations.get("interview:question-bank:scenario:7", "difficulty:2"))
                .thenReturn(List.of(question));
        AtomicInteger databaseCalls = new AtomicInteger();

        List<Question> result = cache.getOrLoad(7L, 2, () -> {
            databaseCalls.incrementAndGet();
            return List.of();
        });

        assertThat(result).containsExactly(question);
        assertThat(databaseCalls).hasValue(0);
        assertThat(cache.stats()).containsEntry("hits", 1L);
    }
}
