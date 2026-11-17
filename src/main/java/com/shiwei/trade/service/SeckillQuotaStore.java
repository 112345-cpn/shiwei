package com.shiwei.trade.service;

import com.shiwei.common.constant.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 秒杀名额的 Redis 读写。单独抽出来是为了打断循环依赖：
 * OrderService 关单时要释放名额，而 SeckillService 又依赖 OrderService 落单。
 * 两边都只依赖这个纯 Redis 组件，就没有环了。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillQuotaStore {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>();

    static {
        RELEASE_SCRIPT.setLocation(new ClassPathResource("lua/seckill_release.lua"));
        RELEASE_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redis;

    public String stockKey(Long voucherId) {
        return RedisKeys.seckillStock(voucherId);
    }

    public String usersKey(Long voucherId) {
        return RedisKeys.seckillUsers(voucherId);
    }

    public boolean initialized(Long voucherId) {
        return Boolean.TRUE.equals(redis.hasKey(stockKey(voucherId)));
    }

    /**
     * 归还一个名额并解除该用户的「已购」标记。
     * 关单后要归还，否则这个用户再也抢不到，那格库存也永远少卖；
     * 退款后同样要归还，他退了就应该能再买。
     *
     * @return false 表示活动已经收尾（key 过期），Redis 侧不需要处理
     */
    public boolean release(Long voucherId, Long userId) {
        Long r = redis.execute(RELEASE_SCRIPT,
                List.of(stockKey(voucherId), usersKey(voucherId)),
                String.valueOf(userId));
        return r != null && r == 1L;
    }

    /**
     * 等事务提交之后再释放。
     * 关单是「更新订单 + 回滚 DB 库存 + 释放 Redis 名额」，前两步在事务里。
     * 如果 Redis 先放出去了、事务又回滚，Redis 就凭空多一张库存 —— 那才是真超卖。
     */
    public void releaseAfterCommit(Long voucherId, Long userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            release(voucherId, userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                release(voucherId, userId);
            }
        });
    }
}
