package com.iflytek.interview.system.vo;

import lombok.Data;

import java.util.List;

@Data
public class LoginVO {

    private String token;

    private Long userId;

    private String username;

    /** 登录用户的角色编码列表。 */
    private List<String> roles;
}
