package com.shiwei.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_verify_record")
public class VerifyRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private String verifyCode;
    private Long shopId;
    private Long staffId;
    private Integer amount;

    private LocalDateTime createTime;
}
