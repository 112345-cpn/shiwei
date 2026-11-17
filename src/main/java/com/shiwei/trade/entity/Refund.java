package com.shiwei.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_refund")
public class Refund {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String refundNo;
    private Long orderId;
    private Long paymentId;
    private Integer amount;
    private String reason;

    /** 0 待审 1 已同意 2 已退款 3 已驳回 */
    private Integer status;

    private Long auditStaffId;
    private LocalDateTime auditTime;
    private LocalDateTime finishTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
