package com.iflytek.interview.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.interview.system.entity.Permission;
import com.iflytek.interview.system.mapper.PermissionMapper;
import com.iflytek.interview.system.service.PermissionService;
import org.springframework.stereotype.Service;

@Service
public class PermissionServiceImpl extends ServiceImpl<PermissionMapper, Permission> implements PermissionService {

    @Override
    public Permission getByCode(String code) {
        return this.lambdaQuery().eq(Permission::getCode, code).one();
    }
}
