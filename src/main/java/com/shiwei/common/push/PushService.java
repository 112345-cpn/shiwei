package com.shiwei.common.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiwei.common.constant.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 业务服务往 WebSocket 网关（gopush）推消息的出口。
 *
 * 走 Redis 发布订阅而不是 MQ：网关本来就订阅 Redis，推送又是「尽力而为」的事
 * ——用户没收到还能刷新页面，不需要 MQ 那种投递保证。
 *
 * 格式是 `uid:json` 的纯文本。网关用 SplitN(payload, ":", 2) 切两段，
 * 只切第一个冒号，所以后面 JSON 里的冒号不会被切坏。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushService {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /**
     * 一律吞掉异常：推送失败不能连累支付、核销这些主流程。
     */
    public void toUser(Long userId, PushType type, Object data) {
        if (userId == null) {
            return;
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "type", type.name(),
                    "data", data == null ? Map.of() : data));
            redis.convertAndSend(RedisKeys.PUSH_CHANNEL, userId + ":" + body);
        } catch (Exception e) {
            log.warn("推送发布失败 userId={} type={}", userId, type, e);
        }
    }
}
