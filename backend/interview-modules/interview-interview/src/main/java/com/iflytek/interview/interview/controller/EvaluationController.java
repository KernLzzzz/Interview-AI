package com.iflytek.interview.interview.controller;

import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.interview.dto.EvaluationCallbackDTO;
import com.iflytek.interview.interview.dto.EvaluationSubmitDTO;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.entity.TaskLog;
import com.iflytek.interview.interview.service.EvaluationTaskService;
import com.iflytek.interview.interview.service.RecordService;
import com.iflytek.interview.interview.vo.EvaluationTaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/evaluations")
@Tag(name = "AI 评测任务", description = "任务提交、状态查询、结果回调")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationTaskService evaluationTaskService;
    private final RecordService recordService;

    @PreAuthorize("hasAuthority('interview:submit')")
    @PostMapping
    @Operation(summary = "提交评测任务", description = "幂等返回同一记录当前有效的评测任务")
    public Result<EvaluationTaskVO> submit(@Valid @RequestBody EvaluationSubmitDTO dto) {
        assertRecordAccess(dto.recordId());
        return Result.success(EvaluationTaskVO.from(
                evaluationTaskService.submit(dto.recordId(), dto.callbackUrl())));
    }

    @PreAuthorize("hasAuthority('record:view')")
    @GetMapping("/{taskId}")
    @Operation(summary = "查询评测任务状态")
    public Result<EvaluationTaskVO> get(@PathVariable Long taskId) {
        TaskLog task = evaluationTaskService.get(taskId);
        assertRecordAccess(task.getBizId());
        return Result.success(EvaluationTaskVO.from(task));
    }

    @PreAuthorize("hasAuthority('record:view')")
    @GetMapping("/records/{recordId}/latest")
    @Operation(summary = "查询面试最近一次评测任务")
    public Result<EvaluationTaskVO> latest(@PathVariable Long recordId) {
        assertRecordAccess(recordId);
        TaskLog task = evaluationTaskService.latestForRecord(recordId);
        if (task == null) {
            throw new BusinessException(ErrorCode.EVALUATION_TASK_NOT_FOUND);
        }
        return Result.success(EvaluationTaskVO.from(task));
    }

    @PostMapping("/callback")
    @Operation(summary = "接收外部 AI 评测结果回调")
    public Result<Void> callback(
            @RequestHeader("X-Evaluation-Callback-Token") String token,
            @Valid @RequestBody EvaluationCallbackDTO dto) {
        evaluationTaskService.completeFromCallback(token, dto);
        return Result.success();
    }

    private void assertRecordAccess(Long recordId) {
        InterviewRecord record = recordService.getById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        if (SecurityUtil.hasRole("candidate")
                && !record.getUserId().equals(SecurityUtil.getCurrentUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
