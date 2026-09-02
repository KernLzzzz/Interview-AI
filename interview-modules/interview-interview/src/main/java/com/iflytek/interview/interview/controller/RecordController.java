package com.iflytek.interview.interview.controller;

import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.service.RecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/records")
@Tag(name = "面试记录", description = "创建、开始、提交、取消面试与列表查询")
public class RecordController {

    @Autowired
    private RecordService recordService;

    /** 创建面试（候选人） */
    @PreAuthorize("hasAuthority('record:create')")
    @PostMapping
    @Operation(summary = "创建面试", description = "按场景创建一条 pending 状态的面试记录")
    public Result<InterviewRecord> create(@RequestBody Map<String, Long> body) {
        Long userId = SecurityUtil.getCurrentUserId();
        Long scenarioId = body.get("scenarioId");
        return Result.success(recordService.createRecord(userId, scenarioId));
    }

    /** 开始面试 */
    @PreAuthorize("hasAuthority('interview:start')")
    @PostMapping("/{id}/start")
    @Operation(summary = "开始面试", description = "pending → ongoing，记录开始时间")
    public Result<InterviewRecord> start(@PathVariable Long id) {
        return Result.success(recordService.startInterview(id, SecurityUtil.getCurrentUserId()));
    }

    /** 提交答案 */
    @PreAuthorize("hasAuthority('interview:submit')")
    @PostMapping("/{id}/submit")
    @Operation(summary = "提交答案", description = "ongoing → completed，落答案数据，完成后可触发异步评测")
    public Result<Void> submit(@PathVariable Long id, @RequestBody Map<String, String> body) {
        recordService.submitInterview(id, SecurityUtil.getCurrentUserId(), body.get("answerData"));
        return Result.success();
    }

    /** 取消面试 */
    @PreAuthorize("hasAuthority('interview:cancel')")
    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消面试", description = "pending → cancelled")
    public Result<Void> cancel(@PathVariable Long id) {
        recordService.cancelInterview(id, SecurityUtil.getCurrentUserId());
        return Result.success();
    }

    /** 面试列表（数据权限：管理员全部，候选人自己的） */
    @PreAuthorize("hasAuthority('record:view')")
    @GetMapping
    @Operation(summary = "面试记录列表", description = "数据权限：管理员看全部，其他角色只看自己的")
    public Result<List<InterviewRecord>> list() {
        return Result.success(recordService.listForCurrentUser());
    }
}
