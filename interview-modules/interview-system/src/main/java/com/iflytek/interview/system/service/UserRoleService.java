package com.iflytek.interview.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.iflytek.interview.system.entity.UserRole;

public interface UserRoleService extends IService<UserRole> {
    // 给用户分配角色
    void assignRole(Long userId, Long roleId);
}
