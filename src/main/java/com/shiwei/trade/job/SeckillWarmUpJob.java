package com.shiwei.trade.job;

import com.shiwei.trade.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 秒杀库存预热。
 * 每 5 分钟扫一遍「10 分钟内开始 + 正在进行」的场次写进 Redis。
 * 提前写而不是等第一个请求回源：那一刻本来就最挤，不能再让第一个用户多等一次 DB 往返。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillWarmUpJob {

    private final SeckillService seckillService;

    @Scheduled(fixedDelay = 300_000, initialDelay = 10_000)
    public void warmUp() {
        try {
            seckillService.warmUp();
        } catch (Exception e) {
            log.error("秒杀预热失败", e);
        }
    }
}
