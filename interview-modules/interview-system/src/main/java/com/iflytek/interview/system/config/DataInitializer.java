package com.iflytek.interview.system.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iflytek.interview.system.entity.Role;
import com.iflytek.interview.system.entity.User;
import com.iflytek.interview.system.entity.UserRole;
import com.iflytek.interview.system.mapper.RoleMapper;
import com.iflytek.interview.system.mapper.UserMapper;
import com.iflytek.interview.system.mapper.UserRoleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动数据初始化：确保 admin 账号存在（admin/admin123，BCrypt + user_role 关联 admin 角色）
 * 幂等：已存在不重复创建；旧库明文密码自动修复为 BCrypt
 */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // 1. 查 admin 用户（init.sql 可能已创建过明文密码版本）
        User admin = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, "admin"));
        if (admin == null) {
            admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setEmail("admin@example.com");
            admin.setStatus(1);
            userMapper.insert(admin);
            log.info("DataInitializer: 已创建管理员账号 admin/admin123");
        } else if (!passwordEncoder.matches("admin123", admin.getPassword())) {
            // 旧库：明文 或 BCrypt(123456) → 统一收敛为 admin/admin123
            admin.setPassword(passwordEncoder.encode("admin123"));
            userMapper.updateById(admin);
            log.info("DataInitializer: 已修复 admin 密码为 BCrypt（admin/admin123）");
        }

        // 2. 确保 admin 挂了 admin 角色（user_role 关联，幂等）
        Role adminRole = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getCode, "admin"));
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, admin.getId())
                .eq(UserRole::getRoleId, adminRole.getId()));
        if (count == 0) {
            UserRole userRole = new UserRole();
            userRole.setUserId(admin.getId());
            userRole.setRoleId(adminRole.getId());
            userRoleMapper.insert(userRole);
            log.info("DataInitializer: 已为 admin 挂上 admin 角色");
        }

        // 3. 面试官账号：统一收敛为 interview/interview123（含字母数字，能通过登录密码强度校验）
        User interviewer = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, "interviewer"));
        if (interviewer != null && !passwordEncoder.matches("interview123", interviewer.getPassword())) {
            interviewer.setPassword(passwordEncoder.encode("interview123"));
            userMapper.updateById(interviewer);
            log.info("DataInitializer: 已修复 interviewer 密码为 BCrypt（interview/interview123）");
        }
    }
}
