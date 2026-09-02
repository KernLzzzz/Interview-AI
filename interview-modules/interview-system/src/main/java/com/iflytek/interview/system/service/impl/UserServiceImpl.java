package com.iflytek.interview.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.interview.system.dto.RegisterDTO;
import com.iflytek.interview.system.entity.Role;
import com.iflytek.interview.system.entity.User;
import com.iflytek.interview.system.entity.UserRole;
import com.iflytek.interview.system.mapper.RoleMapper;
import com.iflytek.interview.system.mapper.UserMapper;
import com.iflytek.interview.system.mapper.UserRoleMapper;
import com.iflytek.interview.system.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public boolean existsByUsername(String username) {
        return this.lambdaQuery().eq(User::getUsername, username).count() > 0;
    }

    @Override
    public User getByUsername(String username) {
        return this.lambdaQuery().eq(User::getUsername, username).one();
    }

    @Override
    public User register(RegisterDTO dto) {
        // ② 创建用户对象，密码用 BCrypt 加密
        User user = new User();
        user.setUsername(dto.getUsername());
        // user.setPassword(DigestUtils.md5DigestAsHex(dto.getPassword().getBytes()));
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setEmail(dto.getEmail());
        user.setStatus(1);

        // ③ 保存到数据库
        save(user);

        // 默认设置当前用户是候选人角色==》获取面试者角色的id写到数据库
        Role candidateRole = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getCode, "candidate"));
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(candidateRole.getId());
        userRoleMapper.insert(userRole);

        return user;
    }
}
