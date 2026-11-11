package com.hmdp.utils;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 通用缓存客户端（缓存三连完整解决方案）
 *
 * 提供三个方法，对应缓存三大经典问题：
 * 1. queryWithPassThrough  —— 解决缓存穿透（布隆过滤器 + 空值缓存）
 * 2. queryWithMutex         —— 解决缓存击穿（互斥锁 + 双检查）
 * 3. queryWithLogicalExpire —— 解决缓存击穿（逻辑过期 + 异步重建）
 *
 * 三个方法都使用"过期时间 + 随机偏移"模式，解决缓存雪崩。
 *
 * @author HM-DianPing
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    // ============================================
    // 工具方法：写缓存（带随机 TTL，防雪崩）
    // ============================================

    /**
     * 写入缓存（带随机过期时间，防止雪崩）
     *
     * @param key   Redis Key
     * @param value 值（自动 JSON 序列化）
     * @param time  过期时间（秒）
     */
    public void set(String key, Object value, Long time) {
        // 关键：TTL 加上随机偏移（±5 分钟），避免同一时间大量 Key 过期
        long randomOffset = RandomUtil.randomLong(0, RedisConstants.CACHE_RANDOM_TTL_RANGE * 60);
        stringRedisTemplate.opsForValue().set(
                key, JSONUtil.toJsonStr(value), time * 60 + randomOffset, TimeUnit.SECONDS);
    }

    /**
     * 写入缓存（带逻辑过期时间，用于击穿防护）
     */
    public void setWithLogicalExpire(String key, Object value, Long time) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(time));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // ============================================
    // 方法一：解决缓存穿透（布隆过滤器 + 空值缓存）
    // ============================================

    /**
     * 查询缓存，缓存穿透防护版
     *
     * 核心流程：
     * 1. 布隆过滤器判断 ID 是否存在（不存在直接返回）
     * 2. 查询 Redis
     * 3. 命中（值非空）→ 返回
     * 4. 命中（值为空标记）→ 返回 null
     * 5. 未命中 → 查 DB
     * 6. DB 存在 → 写缓存，返回
     * 7. DB 不存在 → 写空值标记（2 分钟），返回 null
     *
     * @param keyPrefix  Redis Key 前缀
     * @param id         业务 ID
     * @param type       返回值类型
     * @param dbFallback DB 查询函数
     * @param time       缓存过期时间（分钟）
     * @param bloomFilterName 布隆过滤器名（null 表示不用布隆过滤器）
     * @param <T>        泛型
     * @param <ID>       ID 类型
     */
    public <T, ID> T queryWithPassThrough(String keyPrefix, ID id, Class<T> type,
                                            Function<ID, T> dbFallback, Long time, String bloomFilterName) {
        String key = keyPrefix + id;

        // 1. 布隆过滤器拦截（解决穿透 + 非法 ID 攻击）
        if (bloomFilterName != null) {
            RBloomFilter<Object> bloomFilter = redissonClient.getBloomFilter(bloomFilterName);
            if (bloomFilter.isExists() && !bloomFilter.contains(id)) {
                log.warn("布隆过滤器拦截（ID 不存在）：key={}", key);
                return null;
            }
        }

        // 2. 查询 Redis
        String json = stringRedisTemplate.opsForValue().get(key);

        // 3. 命中非空值
        if (json != null && !json.equals(RedisConstants.CACHE_NULL_VALUE)) {
            return JSONUtil.toBean(json, type);
        }

        // 4. 命中空值标记（防穿透）
        if (json != null && json.equals(RedisConstants.CACHE_NULL_VALUE)) {
            return null;
        }

        // 5. 未命中，查询 DB
        T result = dbFallback.apply(id);

        // 6. DB 不存在，写空值标记（短期缓存）
        if (result == null) {
            stringRedisTemplate.opsForValue().set(
                    key, RedisConstants.CACHE_NULL_VALUE,
                    RedisConstants.CACHE_NULL_EXPIRE, TimeUnit.MINUTES);
            return null;
        }

        // 7. DB 存在，写缓存
        this.set(key, result, time);
        return result;
    }

    // ============================================
    // 方法二：解决缓存击穿（互斥锁 + 双检查）
    // ============================================

    /**
     * 查询缓存，缓存击穿防护版（互斥锁方案）
     *
     * 适用场景：缓存数据需要强一致性（如：金额、库存）
     *
     * 核心流程：
     * 1. 查询 Redis
     * 2. 命中（值非空）→ 返回
     * 3. 命中（值为空标记）→ 返回 null
     * 4. 未命中 → 尝试获取分布式锁
     * 5. 获取锁失败 → 休眠 50ms → 重新查询（递归）
     * 6. 获取锁成功 → 查 DB
     * 7. 写缓存（带随机 TTL） → 释放锁
     *
     * @param keyPrefix  Redis Key 前缀
     * @param id         业务 ID
     * @param type       返回值类型
     * @param dbFallback DB 查询函数
     * @param time       缓存过期时间（分钟）
     * @param <T>        泛型
     * @param <ID>       ID 类型
     */
    public <T, ID> T queryWithMutex(String keyPrefix, ID id, Class<T> type,
                                      Function<ID, T> dbFallback, Long time) {
        String key = keyPrefix + id;

        // 1. 查询 Redis
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 命中
        if (json != null && !json.equals(RedisConstants.CACHE_NULL_VALUE)) {
            return JSONUtil.toBean(json, type);
        }
        if (json != null && json.equals(RedisConstants.CACHE_NULL_VALUE)) {
            return null;
        }

        // 3. 未命中，尝试获取分布式锁
        String lockKey = RedisConstants.LOCK_KEY_PREFIX + key;
        RLock lock = redissonClient.getLock(lockKey);
        T result;
        try {
            // 尝试获取锁（最多等待 1 秒，锁定 10 秒后自动释放）
            boolean acquired = lock.tryLock(1, 10, TimeUnit.SECONDS);
            if (!acquired) {
                // 获取失败，休眠 50ms 后重试
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time);
            }

            // 4. 获取锁成功（双检查：可能其他线程已经写好了缓存）
            json = stringRedisTemplate.opsForValue().get(key);
            if (json != null && !json.equals(RedisConstants.CACHE_NULL_VALUE)) {
                return JSONUtil.toBean(json, type);
            }

            // 5. 查 DB
            result = dbFallback.apply(id);

            // 6. DB 不存在，写空值标记
            if (result == null) {
                stringRedisTemplate.opsForValue().set(
                        key, RedisConstants.CACHE_NULL_VALUE,
                        RedisConstants.CACHE_NULL_EXPIRE, TimeUnit.MINUTES);
                return null;
            }

            // 7. 写缓存（带随机 TTL）
            this.set(key, result, time);

        } catch (InterruptedException e) {
            log.error("获取分布式锁被中断", e);
            throw new RuntimeException(e);
        } finally {
            // 8. 释放锁（Redisson 会自动判断是否当前线程持有）
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        return result;
    }

    // ============================================
    // 方法三：解决缓存击穿（逻辑过期 + 异步重建）
    // ============================================

    /**
     * 缓存重建线程池（专用，避免与其他业务混用）
     */
    private static final java.util.concurrent.ExecutorService CACHE_REBUILD_EXECUTOR =
            java.util.concurrent.Executors.newFixedThreadPool(10);

    /**
     * 查询缓存，缓存击穿防护版（逻辑过期方案）
     *
     * 适用场景：缓存数据允许短期不一致（如：商户信息、博客详情）
     * 优势：查询性能极高，永远不阻塞（即使缓存过期）
     *
     * 核心流程：
     * 1. 查询 Redis
     * 2. 未命中 → 返回 null（必须预热）
     * 3. 命中但未过期 → 返回
     * 4. 命中但已过期 → 获取分布式锁
     * 5. 获取锁成功 → 异步线程重建缓存 + 返回旧数据
     * 6. 获取锁失败 → 返回旧数据
     *
     * @param keyPrefix  Redis Key 前缀
     * @param id         业务 ID
     * @param type       返回值类型
     * @param dbFallback DB 查询函数
     * @param time       逻辑过期时间（分钟）
     * @param <T>        泛型
     * @param <ID>       ID 类型
     */
    public <T, ID> T queryWithLogicalExpire(String keyPrefix, ID id, Class<T> type,
                                              Function<ID, T> dbFallback, Long time) {
        String key = keyPrefix + id;

        // 1. 查询 Redis
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 未命中（必须预热，本方法不处理未命中）
        if (json == null) {
            return null;
        }

        // 3. 反序列化（包含逻辑过期时间）
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        T result = JSONUtil.toBean((cn.hutool.json.JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 4. 未过期，直接返回
        if (expireTime.isAfter(LocalDateTime.now())) {
            return result;
        }

        // 5. 已过期，尝试获取分布式锁
        String lockKey = RedisConstants.LOCK_KEY_PREFIX + key;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(1, 10, TimeUnit.SECONDS);
            if (acquired) {
                // 6. 获取锁成功，异步线程重建缓存
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        // 双检查（防止多个线程都重建了缓存）
                        String doubleCheck = stringRedisTemplate.opsForValue().get(key);
                        if (doubleCheck != null) {
                            RedisData doubleData = JSONUtil.toBean(doubleCheck, RedisData.class);
                            if (doubleData.getExpireTime().isAfter(LocalDateTime.now())) {
                                return;
                            }
                        }
                        // 查 DB + 写缓存
                        T dbResult = dbFallback.apply(id);
                        if (dbResult != null) {
                            this.setWithLogicalExpire(key, dbResult, time);
                        }
                    } catch (Exception e) {
                        log.error("缓存重建异常：key={}", key, e);
                    } finally {
                        lock.unlock();
                    }
                });
            }
        } catch (InterruptedException e) {
            log.error("获取分布式锁被中断", e);
            throw new RuntimeException(e);
        }

        // 7. 返回旧数据（牺牲一致性换高可用）
        return result;
    }
}