package com.iflytek.interview.interview.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.entity.InterviewStatus;
import com.iflytek.interview.interview.dto.AnswerItemDTO;
import com.iflytek.interview.interview.dto.InterviewAnswer;
import com.iflytek.interview.interview.dto.QuestionSnapshot;
import com.iflytek.interview.interview.dto.SubmitInterviewDTO;
import com.iflytek.interview.interview.mapper.InterviewRecordMapper;
import com.iflytek.interview.interview.service.RecordService;
import com.iflytek.interview.interview.service.EvaluationTaskService;
import com.iflytek.interview.interview.vo.EvaluationTaskVO;
import com.iflytek.interview.file.entity.FileRecord;
import com.iflytek.interview.file.service.FileRecordService;
import com.iflytek.interview.question.entity.Question;
import com.iflytek.interview.question.service.QuestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RecordServiceImpl extends ServiceImpl<InterviewRecordMapper, InterviewRecord>
        implements RecordService {

    @Autowired
    private EvaluationTaskService evaluationTaskService;

    @Autowired
    private QuestionService questionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FileRecordService fileRecordService;

    @Override
    @Transactional
    public InterviewRecord createRecord(Long userId, Long scenarioId, String interviewMode) {
        InterviewRecord record = new InterviewRecord();
        record.setUserId(userId);
        record.setScenarioId(scenarioId);
        record.setInterviewMode(interviewMode);
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
        if (questions.isEmpty()) {
            throw new BusinessException(400, "当前场景暂无可用题目");
        }
        List<QuestionSnapshot> snapshot = questions.stream().map(QuestionSnapshot::from).toList();
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
    public EvaluationTaskVO submitInterview(Long recordId, Long userId, SubmitInterviewDTO submission) {
        InterviewRecord record = checkAndGet(recordId, userId);
        checkTransition(record.getStatus(), "completed");
        List<QuestionSnapshot> snapshot;
        try {
            snapshot = objectMapper.readValue(record.getQuestionData(), new TypeReference<>() { });
        } catch (Exception ex) {
            throw new BusinessException(500, "题目快照解析失败");
        }
        Map<Long, AnswerItemDTO> submitted = submission.answers().stream()
                .collect(Collectors.toMap(AnswerItemDTO::questionId, Function.identity(), (left, right) -> right));
        if (submitted.keySet().stream().anyMatch(id -> snapshot.stream().noneMatch(q -> q.id().equals(id)))) {
            throw new BusinessException(400, "答案中包含本次面试之外的题目");
        }
        List<InterviewAnswer> canonicalAnswers = snapshot.stream()
                .map(question -> new InterviewAnswer(question.id(), question.content(),
                        submitted.containsKey(question.id()) ? submitted.get(question.id()).answer() : ""))
                .toList();
        record.setStatus("completed");
        bindMedia(record, userId, submission);
        try {
            record.setAnswerData(objectMapper.writeValueAsString(canonicalAnswers));
        } catch (JsonProcessingException ex) {
            throw new BusinessException(500, "答题数据序列化失败");
        }
        record.setCompletedAt(LocalDateTime.now());
        if (record.getStartedAt() != null) {
            record.setDuration((int) java.time.Duration.between(
                    record.getStartedAt(), record.getCompletedAt()).toSeconds());
        }
        baseMapper.updateById(record);
        return EvaluationTaskVO.from(evaluationTaskService.submit(recordId, null));
    }

    private void bindMedia(InterviewRecord record, Long userId, SubmitInterviewDTO submission) {
        if ("text".equals(record.getInterviewMode())) {
            if (submission.mediaFileId() != null) {
                throw new BusinessException(400, "文本面试不接受录音或录像文件");
            }
            return;
        }
        if (submission.mediaFileId() == null) {
            throw new BusinessException(400, "语音/视频面试需要先上传本场录制文件");
        }
        FileRecord media = fileRecordService.getById(submission.mediaFileId());
        String expectedModule = "video".equals(record.getInterviewMode())
                ? "interview-video" : "interview-audio";
        if (media == null || !userId.equals(media.getUserId()) || !expectedModule.equals(media.getModule())) {
            throw new BusinessException(400, "媒体文件不存在、无权使用或类型不匹配");
        }
        record.setMediaFileId(media.getId());
        record.setMediaDuration(submission.mediaDuration());
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
