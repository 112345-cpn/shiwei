package com.shiwei.trade.mq;

import com.shiwei.common.config.RabbitMQConfig;
import com.shiwei.trade.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 订单超时关单。
 * 延迟不靠 delayed-message 插件，靠「消息在 TTL 队列里躺 15 分钟 → 过期后死信到消费队列」，
 * 这样本地和服务器都不需要额外装插件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutConsumer {

    private final OrderService orderService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ORDER_TIMEOUT)
    public void onTimeout(Long orderId) {
        if (orderId == null) {
            return;
        }
        try {
            // closeIfTimeout 自带幂等：已经付过款的单直接返回 false，不会误关
            if (orderService.closeIfTimeout(orderId)) {
                log.info("订单超时关闭 orderId={}", orderId);
            }
        } catch (Exception e) {
            // 不向外抛：抛出去消息会一直重投，一条坏消息能把队列堵死。
            // 漏关的单由 OrderCloseJob 每分钟扫一遍补上
            log.error("超时关单失败 orderId={}", orderId, e);
        }
    }
}
