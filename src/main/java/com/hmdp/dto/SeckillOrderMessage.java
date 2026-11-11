package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 秒杀订单消息（RabbitMQ 传输对象）
 *
 * 生产者发消息时，传入 userId、voucherId、orderId
 * 消费者收到后做幂等校验，避免重复下单
 *
 * @author HM-DianPing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long userId;

    /** 优惠券 ID */
    private Long voucherId;

    /** 订单 ID（提前通过雪花算法生成） */
    private Long orderId;

    /** 消息发送时间戳（用于幂等校验） */
    private Long timestamp;
}