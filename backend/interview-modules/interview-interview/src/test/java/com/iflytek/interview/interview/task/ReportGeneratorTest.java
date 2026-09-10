package com.iflytek.interview.interview.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.mapper.InterviewRecordMapper;
import com.iflytek.interview.question.entity.Question;
import com.iflytek.interview.question.mapper.QuestionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportGeneratorTest {

    @Test
    void generatesStructuredReportAndBatchLoadsQuestions() throws Exception {
        InterviewRecordMapper recordMapper = mock(InterviewRecordMapper.class);
        QuestionMapper questionMapper = mock(QuestionMapper.class);
        InterviewRecord record = new InterviewRecord();
        record.setId(9L);
        record.setAnswerData("[{\"id\":1,\"content\":\"解释 Redis Hash\",\"answer\":\"Redis Hash 将一个场景的多个难度维度集中存储，并通过 TTL 主动刷新\"}]");
        Question question = new Question();
        question.setId(1L);
        question.setKeywords("Redis,Hash,TTL");
        when(recordMapper.selectById(9L)).thenReturn(record);
        when(questionMapper.selectBatchIds(anyList())).thenReturn(List.of(question));
        RemoteAiEvaluationGateway gateway = mock(RemoteAiEvaluationGateway.class);
        when(gateway.isEnabled()).thenReturn(false);

        ReportGenerator generator = new ReportGenerator(recordMapper, questionMapper, new ObjectMapper(), gateway);
        String report = generator.generate(9L);

        assertThat(report).contains("\"score\":50", "summary", "dimensions", "candidateProfile", "recommendations", "matchedKeywords");
        ArgumentCaptor<InterviewRecord> captor = ArgumentCaptor.forClass(InterviewRecord.class);
        verify(recordMapper).updateById(captor.capture());
        assertThat(captor.getValue().getScore()).isNotNull();
    }
}
