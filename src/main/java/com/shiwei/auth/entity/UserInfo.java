package com.shiwei.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("tb_user_info")
public class UserInfo {

    @TableId(type = IdType.INPUT)
    private Long userId;

    private String city;
    private String introduce;
    private Integer gender;
    private LocalDate birthday;
    private Integer fans;
    private Integer followee;
    private Integer reviewCount;
    private Integer credit;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
