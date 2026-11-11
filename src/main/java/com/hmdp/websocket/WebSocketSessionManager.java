package com.hmdp.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket Session 管理器
 *
 * 用 ConcurrentHashMap 存储所有在线连接，key 为 userId。
 *
 * 线程安全说明：
 * - 使用 ConcurrentHashMap 而非 HashMap
 * - WebSocket 推送时需要加锁或使用原子操作
 *
 * @author HM-DianPing
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    /** 存储 userId -> Session 的映射 */
    private static final Map<Long, WebSocketSession> SESSION_MAP = new ConcurrentHashMap<>();

    /**
     * 添加 Session
     */
    public void add(Long userId, WebSocketSession session) {
        SESSION_MAP.put(userId, session);
        log.info("WebSocket 连接建立：userId={}, 当前在线 {} 人", userId, SESSION_MAP.size());
    }

    /**
     * 移除 Session
     */
    public void remove(Long userId) {
        SESSION_MAP.remove(userId);
        log.info("WebSocket 连接断开：userId={}, 当前在线 {} 人", userId, SESSION_MAP.size());
    }

    /**
     * 获取 Session
     */
    public WebSocketSession get(Long userId) {
        return SESSION_MAP.get(userId);
    }

    /**
     * 检查用户是否在线
     */
    public boolean isOnline(Long userId) {
        WebSocketSession session = SESSION_MAP.get(userId);
        return session != null && session.isOpen();
    }

    /**
     * 推送给指定用户
     */
    public void sendToUser(Long userId, String message) {
        WebSocketSession session = SESSION_MAP.get(userId);
        if (session == null || !session.isOpen()) {
            log.warn("用户不在线，消息推送失败：userId={}", userId);
            return;
        }
        try {
            // WebSocket 是异步的，加锁防并发写
            synchronized (session) {
                session.sendMessage(new org.springframework.web.socket.TextMessage(message));
            }
        } catch (IOException e) {
            log.error("消息推送失败：userId={}", userId, e);
        }
    }

    /**
     * 广播给所有在线用户
     */
    public void broadcast(String message) {
        SESSION_MAP.forEach((userId, session) -> sendToUser(userId, message));
    }

    /**
     * 获取在线人数
     */
    public int onlineCount() {
        return SESSION_MAP.size();
    }
}