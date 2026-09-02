package com.iflytek.interview.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iflytek.interview.common.exception.BusinessException;
import com.iflytek.interview.common.exception.ErrorCode;
import com.iflytek.interview.common.response.Result;
import com.iflytek.interview.common.security.SecurityUtil;
import com.iflytek.interview.common.security.SessionService;
import com.iflytek.interview.system.dto.LoginDTO;
import com.iflytek.interview.system.dto.PasswordDTO;
import com.iflytek.interview.system.dto.RegisterDTO;
import com.iflytek.interview.interview.entity.InterviewRecord;
import com.iflytek.interview.interview.service.RecordService;
import com.iflytek.interview.system.entity.Role;
import com.iflytek.interview.system.entity.User;
import com.iflytek.interview.system.entity.UserRole;
import com.iflytek.interview.system.mapper.RoleMapper;
import com.iflytek.interview.system.mapper.UserMapper;
import com.iflytek.interview.system.mapper.UserRoleMapper;
import com.iflytek.interview.system.service.AuthService;
import com.iflytek.interview.system.service.UserService;
import com.iflytek.interview.system.vo.LoginVO;
import com.iflytek.interview.system.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "用户管理", description = "注册、登录、个人信息")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private RecordService recordService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "用户名/密码/邮箱注册，密码 BCrypt 加密，默认授予 candidate 角色")
    public Result<UserVO> register(@Valid @RequestBody RegisterDTO dto) {

        // ① 校验用户名是否已存在
        if (userService.existsByUsername(dto.getUsername())) {
            throw new BusinessException(ErrorCode.USER_EXISTS);
        }

        // ② 创建用户对象，BCrypt 加密
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());          // 注册填的邮箱要落库
        user.setStatus(1);                       // 默认正常状态（与数据库默认值一致）
        user.setCreatedAt(LocalDateTime.now());  // 插入时间（与数据库默认值一致）
// 原来：user.setPassword(DigestUtils.md5DigestAsHex(dto.getPassword().getBytes()));
// 现在：BCrypt 加密 + 默认挂 candidate 角色
        user.setPassword(passwordEncoder.encode(dto.getPassword()));  // BCrypt 加密
        userMapper.insert(user);                                      // 先入库，拿到自增 userId

        // 新用户默认给 candidate 角色（RBAC：走 user_role 关联表）
        Role candidateRole = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getCode, "candidate"));
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(candidateRole.getId());
        userRoleMapper.insert(userRole);

        // ③ 返回用户信息（不含密码）
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return Result.success(vo);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "登录成功返回 JWT，需放入 Authorization: Bearer <token>")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        // 登录：BCrypt 校验 + 查角色权限 + 写 Redis 会话 + 签发只带会话 key 的 JWT（AuthService 实现）
        return Result.success(authService.login(dto));
    }

    /**
     * 用户登出：删除 Redis 会话，该 token 立即失效
     */
    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "删除 Redis 会话，当前 Token 立即失效")
    public Result<Void> logout() {
        sessionService.removeSession(SecurityUtil.getLoginUser().getSessionId());
        return Result.success();
    }

    /**
     * 我的信息：从 token 拿当前登录用户（无状态认证，身份来自 JWT 而非 URL 参数）
     */
    @GetMapping("/profile")
    @Operation(summary = "我的信息", description = "从 JWT 解析当前登录用户，返回脱敏信息（不含密码）")
    public Result<UserVO> profile() {
        // ① 从 SecurityContext 拿当前用户ID（JwtAuthenticationFilter 解析 token 时写入）
        Long userId = SecurityUtil.getCurrentUserId();
        // ② 查用户
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // ③ 返回脱敏信息（UserVO 不含密码字段）
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return Result.success(vo);
    }

    // 需 user:view 权限（admin 有；candidate 没有）→ 防 IDOR 遍历任意用户
    @PreAuthorize("hasAuthority('user:view')")
    @GetMapping("/{id}")
    @Operation(summary = "查询指定用户", description = "需 user:view 权限（admin 拥有），防止 IDOR 遍历任意用户")
    public Result<UserVO> user(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return Result.success(vo);
    }

    /** 修改个人信息（邮箱/电话/昵称/头像） */
    @PutMapping("/profile")
    @Operation(summary = "修改个人信息", description = "当前登录用户修改邮箱/电话/昵称/头像")
    public Result<UserVO> updateProfile(@RequestBody UserVO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setNickname(dto.getNickname());
        user.setAvatar(dto.getAvatar());
        userService.updateById(user);
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return Result.success(vo);
    }

    /** 修改密码：验旧 → BCrypt 存新 */
    @PutMapping("/password")
    @Operation(summary = "修改密码", description = "校验原密码后 BCrypt 加密存新密码；改密后建议重新登录")
    public Result<Void> changePassword(@Valid @RequestBody PasswordDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userService.updateById(user);
        // 改密后删除该用户全部会话（含当前这个）→ 旧 token 立即 401，强制重新登录
        sessionService.removeUserSessions(userId);
        return Result.success();
    }

    /** 我的面试记录（只返回自己的） */
    @GetMapping("/my/records")
    @Operation(summary = "我的面试记录", description = "只返回当前登录用户自己的面试记录")
    public Result<List<InterviewRecord>> myRecords() {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(recordService.lambdaQuery()
                .eq(InterviewRecord::getUserId, userId)
                .list());
    }
}
