package com.shiwei.common.security;

import com.shiwei.auth.entity.User;
import com.shiwei.common.constant.RedisKeys;
import com.shiwei.common.context.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 登录态存取。除了 token → 用户信息的 Hash，还反向维护一个
 * user → tokens 的集合，这样用户角色变更时能一次性刷掉所有在线会话，
 * 不用扫全库 token。
 */
@Component
@RequiredArgsConstructor
public class TokenStore {

    private final StringRedisTemplate redis;

    public void save(String token, User user, int ttlDays) {
        String key = RedisKeys.TOKEN_PREFIX + token;

        Map<String, String> map = new HashMap<>(4);
        map.put("id", String.valueOf(user.getId()));
        map.put("nickName", user.getNickName());
        map.put("icon", user.getIcon());
        map.put("role", String.valueOf(user.getRole()));

        redis.opsForHash().putAll(key, map);
        redis.expire(key, Duration.ofDays(ttlDays));
        redis.opsForSet().add(RedisKeys.userTokens(user.getId()), token);
        redis.expire(RedisKeys.userTokens(user.getId()), Duration.ofDays(ttlDays));
    }

    public LoginUser get(String token) {
        String key = RedisKeys.TOKEN_PREFIX + token;
        Map<Object, Object> map = redis.opsForHash().entries(key);
        if (map.isEmpty()) {
            return null;
        }
        redis.expire(key, Duration.ofDays(30));
        return new LoginUser(
                Long.valueOf(String.valueOf(map.get("id"))),
                String.valueOf(map.get("nickName")),
                String.valueOf(map.get("icon")),
                Integer.valueOf(String.valueOf(map.get("role")))
        );
    }

    public void remove(String token, Long userId) {
        redis.delete(RedisKeys.TOKEN_PREFIX + token);
        if (userId != null) {
            redis.opsForSet().remove(RedisKeys.userTokens(userId), token);
        }
    }

    /**
     * 用户角色变化后刷新在线会话，避免角色升级了但 token 里还是旧值。
     */
    public void refreshRole(Long userId, Integer role) {
        String setKey = RedisKeys.userTokens(userId);
        Set<String> tokens = redis.opsForSet().members(setKey);
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        for (String token : tokens) {
            String key = RedisKeys.TOKEN_PREFIX + token;
            if (Boolean.TRUE.equals(redis.hasKey(key))) {
                redis.opsForHash().put(key, "role", String.valueOf(role));
            } else {
                redis.opsForSet().remove(setKey, token);
            }
        }
    }
}
