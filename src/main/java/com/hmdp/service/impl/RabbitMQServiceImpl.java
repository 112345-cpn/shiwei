package com.hmdp.service.impl;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.service.IRabbitMQService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * RabbitMQ 服务实现（仅生产者端）
 *
 * 核心流程：
 * 1. 发送方：通过 RabbitTemplate 发送秒杀消息
 *
 * 消费方（@RabbitListener）已迁移到 VoucherOrderServiceImpl，
 * 避免 RabbitMQService 与 VoucherOrderService 之间的循环依赖。
 *
 * @author HM-DianPing
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitMQServiceImpl implements IRabbitMQService {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void sendSeckillOrderMessage(SeckillOrderMessage message) {
        // 只负责「下单」这条消息，不带 TTL。
        // 历史写法在这里给消息设 30s 过期，指望它过期后进死信队列去关单 —— 不成立：
        // 这条消息会被下单消费者立刻取走，等不到过期。延迟关单改由 sendDelayCloseMessage
        // 投到专用延迟队列实现（见 RabbitMQConfig.seckillDelayQueue）。
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_EXCHANGE,
                RabbitMQConfig.SECKILL_ORDER_RK,
                message
        );
        log.info("发送秒杀下单消息：userId={}, voucherId={}, orderId={}",
                message.getUserId(), message.getVoucherId(), message.getOrderId());
    }

    @Override
    public void sendDelayCloseMessage(SeckillOrderMessage message) {
        // 注意：这里【不能】再给消息设置 expiration。
        // 下单消息本身带 30s TTL 是无效的 —— 它会被下单消费者立刻取走、消费掉，
        // TTL 永远等不到期，死信队列也就永远收不到消息（关单不生效）。
        // 正确做法：投到「延迟队列」，该队列没有消费者，靠队列级 x-message-ttl 到期后
        // 通过 x-dead-letter-exchange 转发到死信队列。TTL 在队列上配，见 RabbitMQConfig。
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_EXCHANGE,
                RabbitMQConfig.SECKILL_DELAY_RK,
                message
        );
        log.info("投递延迟关单消息（30s 后进入死信队列）：orderId={}", message.getOrderId());
    }
}
