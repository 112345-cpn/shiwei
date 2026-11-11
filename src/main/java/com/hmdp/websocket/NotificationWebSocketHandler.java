package com.hmdp.websocket;

import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 通知 WebSocket 处理器
 *
 * 连接流程：
 * 1. 客户端发起 WebSocket 握手：ws://host/ws/notification?token=xxx
 * 2. TokenHandshakeInterceptor 校验 token，将 userId 放入 attributes
 * 3. afterConnectionEstablished 触发，加入 SessionManager
 * 4. 客户端发送消息：前端 → 后端 → 推送给指定用户
 * 5. 客户端断开：afterConnectionClosed 触发，从 SessionManager 移除
 *
 * @author HM-DianPing
 */
@Slf4j
@Component
public class NotificationWebSocketHandler implements WebSocketHandler {

    /** userId 在 Session 属性中的 Key */
    public static final String USER_ID_ATTR = "currentUser";

    private final WebSocketSessionManager sessionManager;

    public NotificationWebSocketHandler(WebSocketSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    // ============================================
    // WebSocketHandler 接口实现
    // ============================================

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get(USER_ID_ATTR);
        if (userId == null) {
            log.warn("WebSocket 连接未携带 userId，关闭连接");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        sessionManager.add(userId, session);

        // 发送欢迎消息
        session.sendMessage(new TextMessage("{\"type\":\"connected\",\"msg\":\"连接成功\"}"));
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        Long userId = (Long) session.getAttributes().get(USER_ID_ATTR);
        String payload = message.getPayload().toString();
        log.info("收到 WebSocket 消息：userId={}, payload={}", userId, payload);

        // 简单回显（生产环境根据业务处理）
        session.sendMessage(new TextMessage("{\"type\":\"echo\",\"data\":\"" + payload + "\"}"));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket 传输错误", exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        Long userId = (Long) session.getAttributes().get(USER_ID_ATTR);
        if (userId != null) {
            sessionManager.remove(userId);
        }
        log.info("WebSocket 连接关闭：userId={}, status={}", userId, closeStatus);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    // ============================================
    // 手握拦截器（Token 鉴权）—— 内部静态类
    // ============================================

    /**
     * 手握拦截器：在 WebSocket 建立前执行，可用于 token 校验
     */
    public static class TokenHandshakeInterceptor implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                          WebSocketHandler wsHandler, Map<String, Object> attributes) {
            // 1. 从 URL 中获取 token
            String token = null;
            if (request instanceof ServletServerHttpRequest) {
                ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
                token = servletRequest.getServletRequest().getParameter("token");
            }

            if (token == null || token.isEmpty()) {
                log.warn("WebSocket 握手失败：token 为空");
                return false;
            }

            try {
                // 2. 解析 token 获取 userId
                Long userId = UserHolder.parseToken(token);
                if (userId == null) {
                    log.warn("WebSocket 握手失败：token 无效");
                    return false;
                }

                // 3. 将 userId 放入 attributes，供后续使用
                attributes.put(USER_ID_ATTR, userId);
                return true;
            } catch (Exception e) {
                log.error("WebSocket 握手鉴权失败", e);
                return false;
            }
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                     WebSocketHandler wsHandler, Exception exception) {
            if (exception != null) {
                log.error("WebSocket 握手异常", exception);
            }
        }
    }
}