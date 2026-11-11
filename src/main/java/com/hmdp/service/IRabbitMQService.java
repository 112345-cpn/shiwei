package com.hmdp.service;

import com.hmdp.dto.SeckillOrderMessage;

/**
 * RabbitMQ 服务接口
 *
 * 只负责发送消息（生产者端），消费者逻辑由 VoucherOrderServiceImpl 直接处理，
 * 避免 RabbitMQService 与 VoucherOrderService 之间形成循环依赖。
 *
 * @author HM-DianPing
 */
public interface IRabbitMQService {

    /**
     * 发送秒杀下单消息
     *
     * @param message 秒杀订单消息
     */
    void sendSeckillOrderMessage(SeckillOrderMessage message);

    /**
     * 发送「延迟关单」消息（下单成功后调用）
     *
     * 消息投递到延迟队列（无消费者），队列级 TTL 30s 到期后转为死信，
     * 由死信队列的消费者执行关单 + 库存回补。
     *
     * @param message 秒杀订单消息
     */
    void sendDelayCloseMessage(SeckillOrderMessage message);
}
