package com.shiwei.common.push;

/**
 * 推送事件类型。前端按这个值决定怎么处理消息，网关只负责转发不解析。
 */
public enum PushType {

    /** 秒杀结果，data 是 SeckillResultVO */
    SECKILL_RESULT,

    /** 支付成功，券已到账 */
    ORDER_PAID,

    /** 核销完成 */
    ORDER_VERIFIED,

    /** 退款状态变化 */
    REFUND_CHANGED
}
