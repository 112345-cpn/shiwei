package com.shiwei.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_payment")
public class Payment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String payNo;
    private Long orderId;

    /** 0 余额 1 模拟支付 2 支付宝 */
    private Integer channel;
    private Integer amount;

    /** 0 待付 1 成功 2 失败 3 已退 */
    private Integer status;

    private String externalNo;
    private LocalDateTime notifyTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
