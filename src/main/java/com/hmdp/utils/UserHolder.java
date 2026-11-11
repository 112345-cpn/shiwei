package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user) {
        tl.set(user);
    }

    public static UserDTO getUser() {
        return tl.get();
    }

    public static void removeUser() {
        tl.remove();
    }

    /**
     * 从 Redis Token 解析用户 ID（供 WebSocket 握手使用）
     *
     * @param token 前端传来的 token
     * @return userId，如果 token 无效返回 null
     */
    public static Long parseToken(String token) {
        try {
            StringRedisTemplate template = SpringContextHolder.getBean(StringRedisTemplate.class);
            Map<Object, Object> entries = template.opsForHash().entries("login:token:" + token);
            if (entries == null || entries.isEmpty()) {
                return null;
            }
            Object userId = entries.get("id");
            return userId == null ? null : Long.valueOf(userId.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
