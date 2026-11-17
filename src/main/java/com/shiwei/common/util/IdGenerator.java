package com.shiwei.common.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 全局自增 id 与业务单号。
 * 用 Redis INCR 保证多实例下不重复，比数据库自增更适合先生成号再落库的场景。
 */
@Component
@RequiredArgsConstructor
public class IdGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redis;

    public long nextId(String biz) {
        String key = "shiwei:seq:" + biz + ":" + LocalDate.now().format(DAY);
        Long v = redis.opsForValue().increment(key);
        redis.expire(key, Duration.ofDays(3));
        return v == null ? 0 : v;
    }

    public String nextNo(String prefix, String biz) {
        return prefix + LocalDate.now().format(DAY) + String.format("%08d", nextId(biz));
    }

    public String nextOrderNo() {
        return nextNo("SW", "order");
    }

    public String nextPayNo() {
        return nextNo("PAY", "pay");
    }

    public String nextRefundNo() {
        return nextNo("RF", "refund");
    }

    public String nextBatchNo() {
        return nextNo("RC", "reconcile");
    }
}
