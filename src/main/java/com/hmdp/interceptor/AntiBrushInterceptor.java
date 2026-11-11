package com.hmdp.interceptor;

import com.hmdp.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 用户级防刷拦截器（基于 Redis 计数器）
 *
 * 作用：限制单个请求主体在 1 秒内的请求次数（默认 5 次）
 *
 * 实现：Redis INCR + 窗口过期，属于**固定窗口**算法。
 *
 * 固定窗口的已知缺陷（面试会问）：
 * 如果窗口按「第一次请求的时间」起算，攻击者可以在第 0.9 秒打 5 次、第 1.1 秒再打 5 次，
 * 0.2 秒内实际通过 10 次 —— 也就是窗口边界处允许双倍流量穿透。
 * 要彻底解决需要换成滑动窗口（ZSet 存时间戳）或令牌桶；本项目的量级用固定窗口够用，
 * 真正的接口级限流由 Sentinel 承担。
 *
 * 限流主体：
 * 登录用户按 userId 计，未登录按 IP 计。原来未登录时直接放行，
 * 等于不带 token 就能绕过整个防刷，这里改掉了。
 *
 * @author HM-DianPing
 */
@Slf4j
@RequiredArgsConstructor
public class AntiBrushInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    /** 单主体 QPS 上限 */
    private static final int USER_QPS_LIMIT = 5;

    /** 时间窗口（1 秒） */
    private static final int WINDOW_SECONDS = 1;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 确定限流主体：登录了按 userId，没登录按 IP
        String subject;
        try {
            subject = "user:" + UserHolder.getUser().getId();
        } catch (Exception e) {
            // 未登录不能直接放行 —— 那样不带 token 就能绕过限流
            subject = "ip:" + request.getRemoteAddr();
        }

        // 2. 构造 Redis Key
        String key = "anti:brush:" + subject;

        // 3. Redis 计数器（INCR + 第一次设置过期）
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Duration.ofSeconds(WINDOW_SECONDS));
        }

        // 4. 判断是否超限
        if (count != null && count > USER_QPS_LIMIT) {
            log.warn("触发限流：subject={}, count={}, limit={}", subject, count, USER_QPS_LIMIT);
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write("{\"success\":false,\"errorMsg\":\"操作过于频繁，请稍后再试\"}");
            } catch (Exception ignored) {}
            return false;
        }

        return true;
    }
}