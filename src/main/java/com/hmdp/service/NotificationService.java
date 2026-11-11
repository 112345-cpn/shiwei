package com.hmdp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.hmdp.websocket.WebSocketSessionManager;

/**
 * 实时通知服务（WebSocket 推送门面）
 *
 * 任何业务模块只要需要推送通知给用户，直接调用本服务：
 * <pre>
 * {@code
 * notificationService.pushToUser(userId, new NotificationMessage("comment", "你的博客有新评论"));
 * }
 * </pre>
 *
 * @author HM-DianPing
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final WebSocketSessionManager sessionManager;

    /**
     * 用 Spring 容器里的 ObjectMapper，而不是自己 new 一个。
     *
     * 原因：全局的 Long → String 序列化规则（JacksonConfig）注册在这个 ObjectMapper 上。
     * 之前这里用 Hutool 的 JSONUtil.toJsonStr 序列化，它不认 Jackson 的配置，
     * 结果 19 位雪花订单号在 WS 推送里仍然是 JSON number，前端 JSON.parse 就丢精度
     * （实测同一订单在页面上显示成 ...852 和 ...800 两个值）。
     * 换成这个 mapper 后，HTTP 与 WS 两条通道的口径一致。
     */
    private final ObjectMapper objectMapper;

    /**
     * 推送给指定用户
     */
    public void pushToUser(Long userId, Object message) {
        if (!sessionManager.isOnline(userId)) {
            log.debug("用户不在线，跳过推送：userId={}", userId);
            return;
        }
        String json = toJson(message);
        sessionManager.sendToUser(userId, json);
        log.info("推送消息给用户：userId={}, message={}", userId, json);
    }

    /**
     * 广播给所有在线用户
     */
    public void broadcast(Object message) {
        String json = toJson(message);
        sessionManager.broadcast(json);
        log.info("广播消息：message={}, onlineCount={}", json, sessionManager.onlineCount());
    }

    /**
     * 检查用户是否在线
     */
    public boolean isOnline(Long userId) {
        return sessionManager.isOnline(userId);
    }

    /**
     * 获取在线人数
     */
    public int onlineCount() {
        return sessionManager.onlineCount();
    }

    /**
     * 对象转 JSON（避免依赖 Jackson，使用 Hutool）
     */
    private String toJson(Object obj) {
        if (obj == null) return "{}";
        if (obj instanceof String) return (String) obj;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("消息序列化失败，降级为 Hutool 序列化（注意：Long 会退化成数字）", e);
            return cn.hutool.json.JSONUtil.toJsonStr(obj);
        }
    }
}