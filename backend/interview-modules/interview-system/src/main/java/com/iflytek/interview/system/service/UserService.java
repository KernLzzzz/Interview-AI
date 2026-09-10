package com.iflytek.interview.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.iflytek.interview.system.dto.RegisterDTO;
import com.iflytek.interview.system.entity.User;

public interface UserService extends IService<User> {
    // 判断用户名是否已存在
    boolean existsByUsername(String username);

    // 根据用户名查找用户
    User getByUsername(String username);

    User register(RegisterDTO dto);
}
