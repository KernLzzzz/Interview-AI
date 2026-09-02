package com.iflytek.interview.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.interview.system.entity.Role;
import com.iflytek.interview.system.mapper.RoleMapper;
import com.iflytek.interview.system.service.RoleService;
import org.springframework.stereotype.Service;

@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService {

    @Override
    public Role getByCode(String code) {
        return this.lambdaQuery().eq(Role::getCode, code).one();
    }
}