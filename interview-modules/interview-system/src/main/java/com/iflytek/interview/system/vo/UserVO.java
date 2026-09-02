package com.iflytek.interview.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {

    private Long id;

    private String username;

    private String email;

    private String nickname;

    private String phone;

    private String avatar;

    private Integer status;

    private LocalDateTime createdAt;
}