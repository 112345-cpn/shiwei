package com.shiwei.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀扩展表。一张券要么走普通售卖，要么走秒杀，靠这张表里有没有记录来区分。
 * 库存这里是「活动初始库存」，真实扣减发生在 Redis；DB 的 stock 由落单时的条件更新兜底。
 */
@Data
@TableName("tb_seckill_voucher")
public class SeckillVoucher {

    @TableId(type = IdType.INPUT)
    private Long voucherId;

    private Integer stock;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
