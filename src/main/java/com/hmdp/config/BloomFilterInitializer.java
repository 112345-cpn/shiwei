package com.hmdp.config;

import com.hmdp.service.IShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 布隆过滤器初始化器
 *
 * 应用启动时：
 * 1. 创建布隆过滤器（如果不存在）
 * 2. 把全量商户 ID 灌进布隆过滤器
 *
 * 作用：拦截不存在的 ID，防止缓存穿透 + 恶意攻击
 *
 * @author HM-DianPing
 */
@Slf4j
@Component
@Order(1)  // 优先级最高，先于其他初始化器
@RequiredArgsConstructor
public class BloomFilterInitializer implements CommandLineRunner {

    private final RedissonClient redissonClient;
    private final IShopService shopService;

    /** 预计商户数量（用于布隆过滤器容量预估） */
    private static final long EXPECTED_SHOP_COUNT = 100_000L;

    /** 误判率（5% 是工业级常用值，平衡空间和准确性） */
    private static final double FALSE_POSITIVE_RATE = 0.05;

    @Override
    public void run(String... args) {
        log.info("开始初始化布隆过滤器...");
        try {
            RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter("bloom:filter:shop");

            // 1. 初始化布隆过滤器（如果已存在则跳过）
            if (!bloomFilter.isExists()) {
                bloomFilter.tryInit(EXPECTED_SHOP_COUNT, FALSE_POSITIVE_RATE);
                log.info("布隆过滤器创建成功：capacity={}, falseRate={}",
                        EXPECTED_SHOP_COUNT, FALSE_POSITIVE_RATE);
            }

            // 2. 灌入全量商户 ID
            List<com.hmdp.entity.Shop> shops = shopService.list();
            for (com.hmdp.entity.Shop shop : shops) {
                bloomFilter.add(shop.getId());
            }
            log.info("布隆过滤器初始化完成：共 {} 条商户数据", shops.size());

            // 3. 顺便预热热点缓存
            shopService.warmUpCache();
        } catch (Exception e) {
            log.error("布隆过滤器初始化失败", e);
        }
    }
}