package com.shiwei.common.push;

import com.shiwei.common.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * 业务服务往网关推消息的出口。
 *
 * 一律吞掉异常：推送是「锦上添花」，用户没收到实时提醒还能刷新页面看到，
 * 但不能因为 MQ 挂了让支付、核销这些主流程跟着失败。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushService {

    private final RabbitTemplate rabbitTemplate;

    public void toUser(Long userId, PushType type, Object data) {
        if (userId == null) {
            return;
        }
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_TRADE,
                    RabbitMQConfig.RK_WS_PUSH,
                    new PushMessage(userId, type.name(), data));
        } catch (Exception e) {
            log.warn("推送投递失败 userId={} type={}", userId, type, e);
        }
    }
}
