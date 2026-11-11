package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 配置
 *
 * 用于分布式锁、分布式限流、分布式集合等场景。
 * 解决了传统 SETNX 锁的以下痛点：
 * - 锁过期时间不好估算（Redisson 自带看门狗自动续期）
 * - 不可重入（Redisson 天然可重入）
 * - 非阻塞（Redisson 提供 tryLock）
 *
 * @author HM-DianPing
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                .setDatabase(database)
                .setPassword(redisPassword.isEmpty() ? null : redisPassword)
                // 连接池配置
                .setConnectionPoolSize(32)
                .setConnectionMinimumIdleSize(8)
                // 连接超时（防止 Redis 阻塞时阻塞应用）
                .setConnectTimeout(3000)
                // 命令等待超时
                .setTimeout(3000)
                // 重试次数
                .setRetryAttempts(3);
        return Redisson.create(config);
    }
}