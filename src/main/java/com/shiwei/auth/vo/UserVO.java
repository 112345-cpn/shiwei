package com.shiwei.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {

    private Long id;
    private String phone;
    private String nickName;
    private String icon;
    private Integer role;
}
