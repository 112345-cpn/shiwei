package com.hmdp.config;

import com.hmdp.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置
 *
 * 端点：/ws/notification
 *
 * 鉴权方案：使用 Query 参数传递 token（因为浏览器 WebSocket 不支持自定义 Header）
 *  示例：ws://localhost:8081/ws/notification?token=xxx
 *
 * @author HM-DianPing
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler notificationHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册 WebSocket 端点 + 鉴权拦截器
        registry.addHandler(notificationHandler, "/ws/notification")
                .addInterceptors(new NotificationWebSocketHandler.TokenHandshakeInterceptor())
                // 允许跨域（生产环境请精确限制）
                .setAllowedOriginPatterns("*");
    }
}