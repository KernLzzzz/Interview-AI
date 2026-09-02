package com.iflytek.interview.interview.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.entity.InterviewStatus;
import com.iflytek.interview.interview.mapper.InterviewRecordMapper;
import com.iflytek.interview.interview.service.RecordService;
import com.iflytek.interview.interview.task.ReportGenerator;
import com.iflytek.interview.question.entity.Question;
import com.iflytek.interview.question.service.QuestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RecordServiceImpl extends ServiceImpl<InterviewRecordMapper, InterviewRecord>
        implements RecordService {

    @Autowired
    private ReportGenerator reportGenerator;

    @Autowired
    private QuestionService questionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional
    public InterviewRecord createRecord(Long userId, Long scenarioId) {
        InterviewRecord record = new InterviewRecord();
        record.setUserId(userId);
        record.setScenarioId(scenarioId);
        record.setStatus(InterviewStatus.PENDING.getCode());
        baseMapper.insert(record);
        log.info("创建面试: recordId={}, userId={}", record.getId(), userId);
        return record;
    }

    @Override
    @Transactional
    public InterviewRecord startInterview(Long recordId, Long userId) {
        InterviewRecord record = checkAndGet(recordId, userId);
        checkTransition(record.getStatus(), "ongoing");
        record.setStatus("ongoing");
        record.setStartedAt(LocalDateTime.now());
        // 开始即抽题：按难度比例 3:5:2 从场景抽 5 道，题目快照存 record（作答与后续评分的依据）
        List<Question> questions = questionService.getRandomQuestionsByRatio(record.getScenarioId(), 5);
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (Question q : questions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", q.getId());
            item.put("content", q.getContent());
            item.put("difficulty", q.getDifficulty());
            snapshot.add(item);
        }
        try {
            record.setQuestionData(objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "题目快照序列化失败");
        }
        baseMapper.updateById(record);
        log.info("开始面试: recordId={}, 抽取题目 {} 道", recordId, questions.size());
        return record;
    }

    @Override
    @Transactional
    public void submitInterview(Long recordId, Long userId, String answerData) {
        InterviewRecord record = checkAndGet(recordId, userId);
        checkTransition(record.getStatus(), "completed");
        record.setStatus("completed");
        record.setAnswerData(answerData);
        record.setCompletedAt(LocalDateTime.now());
        baseMapper.updateById(record);
        // 钩子：提交后触发异步评测（带 task_log：running→success/failed，失败可被定时重试）
        reportGenerator.generateReportWithLog(recordId);
    }

    @Override
    @Transactional
    public void cancelInterview(Long recordId, Long userId) {
        InterviewRecord record = checkAndGet(recordId, userId);
        checkTransition(record.getStatus(), "cancelled");
        record.setStatus("cancelled");
        baseMapper.updateById(record);
    }

    /** 列表：按数据权限返回（admin / 面试官看全部以便评分；候选人只看自己的） */
    public List<InterviewRecord> listForCurrentUser() {
        if (!SecurityUtil.hasRole("candidate")) {
            return this.list();
        }
        Long userId = SecurityUtil.getCurrentUserId();
        return this.lambdaQuery().eq(InterviewRecord::getUserId, userId).list();
    }

    /** 取记录 + 行级数据权限（只能操作自己的，除非管理员） */
    private InterviewRecord checkAndGet(Long recordId, Long userId) {
        InterviewRecord record = baseMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        if (!SecurityUtil.hasRole("admin") && !record.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return record;
    }

    private void checkTransition(String from, String to) {
        if (!InterviewStatus.canTransition(from, to)) {
            throw new BusinessException(400,
                    String.format("非法状态转换: %s → %s", from, to));
        }
    }
}
