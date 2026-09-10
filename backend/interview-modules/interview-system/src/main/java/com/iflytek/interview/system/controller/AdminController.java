package com.iflytek.interview.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.common.security.RequireRecordEditor;
import com.iflytek.interview.common.security.SessionService;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.mapper.InterviewRecordMapper;
import com.iflytek.interview.system.entity.Role;
import com.iflytek.interview.system.entity.User;
import com.iflytek.interview.system.entity.UserRole;
import com.iflytek.interview.system.mapper.RoleMapper;
import com.iflytek.interview.system.mapper.UserMapper;
import com.iflytek.interview.system.mapper.UserRoleMapper;
import com.iflytek.interview.system.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "管理后台", description = "用户列表、角色分配、角色查询")
public class AdminController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private InterviewRecordMapper interviewRecordMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SessionService sessionService;

    /** 用户列表（分页）：Page 查询需要分页插件（MybatisPlusConfig），否则 total 恒为 0 */
    @PreAuthorize("hasAuthority('user:view')")
    @GetMapping("/users")
    @Operation(summary = "用户列表（分页）", description = "分页返回，UserVO 不含密码天然脱敏；参数 current 页码(默认1)、size 每页条数(默认10)")
    public Result<Page<UserVO>> listUsers(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        Page<User> page = userMapper.selectPage(new Page<>(current, size), null);
        // 实体 → VO 脱敏后放进分页对象（保留 records/total/size/current 分页结构）
        Page<UserVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream()
                .map(u -> {
                    UserVO vo = new UserVO();
                    BeanUtils.copyProperties(u, vo);
                    return vo;
                })
                .collect(Collectors.toList()));
        return Result.success(voPage);
    }

    /**
     * 面试评分：人工复核并覆盖 AI 分数，评语 merge 进 aiFeedback JSON（保留原逐题点评）。
     * 需 record:score 权限（admin/面试官），candidate 无 → 403
     */
    @RequireRecordEditor
    @PostMapping("/records/{id}/score")
    @Operation(summary = "面试评分", description = "需 record:score 权限（admin/面试官）；body {score:0-100, comment?:string}")
    public Result<Void> scoreRecord(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        InterviewRecord record = interviewRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        Object scoreObj = body.get("score");
        if (scoreObj == null) {
            throw new BusinessException(400, "评分不能为空");
        }
        int score = ((Number) scoreObj).intValue();
        if (score < 0 || score > 100) {
            throw new BusinessException(400, "评分需在 0-100 之间");
        }
        record.setScore(new BigDecimal(score));

        Object commentObj = body.get("comment");
        String comment = commentObj == null ? "" : commentObj.toString().trim();
        if (StringUtils.hasText(comment)) {
            record.setAiFeedback(mergeInterviewerComment(record.getAiFeedback(), comment));
        }
        interviewRecordMapper.updateById(record);
        return Result.success();
    }

    /** 把面试官评语 merge 进 aiFeedback JSON（兼容旧文本/空值） */
    private String mergeInterviewerComment(String aiFeedback, String comment) {
        try {
            if (StringUtils.hasText(aiFeedback)) {
                Map<String, Object> parsed = objectMapper.readValue(
                        aiFeedback, new TypeReference<Map<String, Object>>() {});
                parsed.put("interviewerComment", comment);
                return objectMapper.writeValueAsString(parsed);
            }
        } catch (Exception e) {
            // aiFeedback 不是 JSON，回退到包装
        }
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("summary", aiFeedback);
        wrapped.put("items", Collections.emptyList());
        wrapped.put("interviewerComment", comment);
        try {
            return objectMapper.writeValueAsString(wrapped);
        } catch (JsonProcessingException e) {
            return aiFeedback == null ? comment : aiFeedback + "\n面试官评语：" + comment;
        }
    }

    /** 给用户分配角色（写 user_role 关联表，多选）——先删后插，多步写，必须加事务 */
    @PreAuthorize("hasAuthority('user:update')")
    @Transactional
    @PutMapping("/users/{id}/role")
    @Operation(summary = "分配角色", description = "先删后插 user_role，多步写操作由 @Transactional 保证一致性；改完删除该用户全部会话，旧 token 立即 401，重登录后拿到新权限")
    public Result<Void> assignRoles(@PathVariable Long id,
                                    @RequestBody List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id));
        for (Long roleId : roleIds) {
            UserRole ur = new UserRole();
            ur.setUserId(id);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
        // 会话里缓存的是旧角色/权限（登录时写入 Redis）→ 删全部会话强制重登录，新权限即时生效
        sessionService.removeUserSessions(id);
        return Result.success();
    }

    /** 角色列表（便于勾选） */
    @PreAuthorize("hasAuthority('user:view')")
    @GetMapping("/roles")
    @Operation(summary = "角色列表", description = "前端分配角色时的勾选项")
    public Result<List<Role>> listRoles() {
        return Result.success(roleMapper.selectList(null));
    }
}
