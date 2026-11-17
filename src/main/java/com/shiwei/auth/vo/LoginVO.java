package com.shiwei.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    private String token;
    private UserVO user;
    /** 仅开发环境回填，生产为 null */
    private String devCode;
}
