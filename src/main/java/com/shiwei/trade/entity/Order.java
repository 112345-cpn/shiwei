package com.shiwei.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_order")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private Long userId;
    private Long voucherId;
    private Long shopId;
    private Long merchantId;

    /** 下单时的商品快照，券改价不影响历史订单 */
    private String title;
    private Integer quantity;
    private Integer unitPrice;
    private Integer payAmount;

    /** 0 待支付 1 待使用 2 已核销 3 退款中 4 已退款 5 已关闭 */
    private Integer status;

    private String verifyCode;
    private LocalDateTime payTime;
    private LocalDateTime verifyTime;
    private Long verifyStaffId;
    private LocalDateTime expireTime;
    private LocalDateTime refundTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
