package com.shiwei.trade.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shiwei.common.config.RabbitMQConfig;
import com.shiwei.common.constant.RedisKeys;
import com.shiwei.common.enums.OrderStatus;
import com.shiwei.common.exception.BizException;
import com.shiwei.common.exception.ErrorCode;
import com.shiwei.trade.entity.Order;
import com.shiwei.trade.entity.SeckillVoucher;
import com.shiwei.trade.entity.Voucher;
import com.shiwei.trade.mapper.OrderMapper;
import com.shiwei.trade.mapper.SeckillVoucherMapper;
import com.shiwei.trade.mapper.VoucherMapper;
import com.shiwei.trade.mq.SeckillMessage;
import com.shiwei.trade.vo.SeckillResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀。整条链路分三段，每段负责一件不同的事：
 *
 * 1. Redis + Lua 判库存、判一人一单、扣减 —— 原子完成，扛住瞬时并发
 * 2. MQ 异步落单 —— 把「写数据库」从请求链路里摘出去，接口只回一个「排队中」
 * 3. DB 条件扣减兜底 —— Redis 和数据库万一不一致，最后一道还能拦住超卖
 *
 * 为什么不让接口直接落库：1500 QPS 打进来，数据库连接池先被打爆，
 * 后面的请求全在等连接，响应时间从毫秒劣化到秒级，等于雪崩。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT = new DefaultRedisScript<>();

    static {
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private static final Duration RESULT_TTL = Duration.ofMinutes(10);
    private static final Duration PENDING_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;
    private final SeckillQuotaStore quotaStore;
    private final SeckillVoucherMapper seckillMapper;
    private final VoucherMapper voucherMapper;
    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 抢购。成功不代表下单成功，只代表「拿到资格」，真正的单子在消费者里落。
     */
    public SeckillResultVO seckill(Long userId, Long voucherId) {
        SeckillVoucher sv = seckillMapper.selectById(voucherId);
        if (sv == null) {
            throw BizException.of(ErrorCode.VOUCHER_NOT_FOUND, "该券不支持抢购");
        }

        // 秒杀表只说明「有这场活动」，券本身可能已经被商家下架了
        Voucher v = voucherMapper.selectById(voucherId);
        if (v == null || v.getStatus() == null || v.getStatus() != 1) {
            throw BizException.of(ErrorCode.VOUCHER_OFF_SHELF);
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(sv.getBeginTime())) {
            throw BizException.of(ErrorCode.VOUCHER_NOT_STARTED);
        }
        if (now.isAfter(sv.getEndTime())) {
            throw BizException.of(ErrorCode.VOUCHER_EXPIRED);
        }

        String resultKey = RedisKeys.seckillResult(voucherId, userId);
        String cached = redis.opsForValue().get(resultKey);
        if (cached != null) {
            // 连点、刷新重试都会打到这里，直接回上次的结果，不再走 Lua
            return parse(cached);
        }

        Long code = redis.execute(SECKILL_SCRIPT,
                List.of(quotaStore.stockKey(voucherId), quotaStore.usersKey(voucherId)),
                String.valueOf(userId));

        if (code == null) {
            throw BizException.of(ErrorCode.INTERNAL_ERROR);
        }
        if (code == 3L) {
            // 库存 key 不在 = 没预热或者活动已收尾
            throw BizException.of(ErrorCode.VOUCHER_NOT_STARTED, "活动还没开始，稍等");
        }
        if (code == 2L) {
            throw BizException.of(ErrorCode.VOUCHER_LIMIT_EXCEEDED, "每人限购 1 张");
        }
        if (code == 1L) {
            throw BizException.of(ErrorCode.VOUCHER_SOLD_OUT);
        }

        // 先写 PENDING 再发消息。反过来做的话，消费者可能比这里更快写入 SUCCESS，
        // 然后被 PENDING 覆盖掉，用户就永远停在「排队中」
        redis.opsForValue().set(resultKey, SeckillResultVO.PENDING, PENDING_TTL);
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_SECKILL, RabbitMQConfig.RK_SECKILL,
                    new SeckillMessage(voucherId, userId));
        } catch (Exception e) {
            // 消息没发出去，这次扣的库存必须当场还回去，否则这一格永远消失
            redis.delete(resultKey);
            rollback(voucherId, userId);
            log.error("秒杀消息投递失败 voucherId={} userId={}", voucherId, userId, e);
            throw BizException.of(ErrorCode.INTERNAL_ERROR, "下单繁忙，请重试");
        }

        return SeckillResultVO.pending();
    }

    /**
     * 消费者调用：真正落单。
     * MQ 至少投递一次，所以这里必须幂等 —— 先看该用户这张券有没有未关闭的单。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(SeckillMessage msg) {
        Long voucherId = msg.getVoucherId();
        Long userId = msg.getUserId();

        Order exist = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getUserId, userId)
                .eq(Order::getVoucherId, voucherId)
                .ne(Order::getStatus, OrderStatus.CLOSED.getCode())
                .last("limit 1"));
        if (exist != null) {
            return exist.getId();
        }

        Voucher v = voucherMapper.selectById(voucherId);
        if (v == null) {
            throw BizException.of(ErrorCode.VOUCHER_NOT_FOUND);
        }

        // Redis 只是前置闸门，数据库条件更新才是最后一道。两边库存不一致时，这里能拦住超卖
        if (voucherMapper.deductStock(voucherId, 1) == 0) {
            throw BizException.of(ErrorCode.VOUCHER_SOLD_OUT);
        }

        return orderService.createPendingOrder(userId, v, 1);
    }

    public SeckillResultVO result(Long userId, Long voucherId) {
        String cached = redis.opsForValue().get(RedisKeys.seckillResult(voucherId, userId));
        if (cached == null) {
            return SeckillResultVO.fail("没有抢购记录");
        }
        return parse(cached);
    }

    public void markSuccess(Long voucherId, Long userId, Long orderId) {
        redis.opsForValue().set(RedisKeys.seckillResult(voucherId, userId),
                SeckillResultVO.SUCCESS + ":" + orderId, RESULT_TTL);
    }

    public void markFail(Long voucherId, Long userId, String reason) {
        redis.opsForValue().set(RedisKeys.seckillResult(voucherId, userId),
                SeckillResultVO.FAIL + ":" + reason, RESULT_TTL);
    }

    /**
     * 把预扣的库存还回去，同时把用户从「已购」集合里移除，否则他永远被判重复下单。
     */
    public void rollback(Long voucherId, Long userId) {
        quotaStore.release(voucherId, userId);
        redis.delete(RedisKeys.seckillResult(voucherId, userId));
    }

    /**
     * 预热。用 setIfAbsent 而不是 set：
     * 活动进行中如果被重新预热一次，把库存重置回初始值就等于凭空多出一批货。
     *
     * @return 本次真正写入的券张数
     */
    public int warmUp() {
        LocalDateTime now = LocalDateTime.now();
        List<SeckillVoucher> list = seckillMapper.selectNeedWarmUp(now.plusMinutes(10));
        if (list.isEmpty()) {
            return 0;
        }

        int warmed = 0;
        for (SeckillVoucher sv : list) {
            Duration ttl = Duration.between(now, sv.getEndTime()).plusDays(1);
            Boolean ok = redis.opsForValue().setIfAbsent(
                    quotaStore.stockKey(sv.getVoucherId()),
                    String.valueOf(sv.getStock()),
                    ttl);
            if (Boolean.TRUE.equals(ok)) {
                redis.expire(quotaStore.usersKey(sv.getVoucherId()), ttl);
                warmed++;
            }
        }
        log.info("秒杀预热：待处理 {} 张，实际写入 {} 张", list.size(), warmed);
        return warmed;
    }

    private SeckillResultVO parse(String raw) {
        if (SeckillResultVO.PENDING.equals(raw)) {
            return SeckillResultVO.pending();
        }
        int idx = raw.indexOf(':');
        String status = idx < 0 ? raw : raw.substring(0, idx);
        String rest = idx < 0 ? "" : raw.substring(idx + 1);
        if (SeckillResultVO.SUCCESS.equals(status)) {
            return SeckillResultVO.success(Long.valueOf(rest));
        }
        return SeckillResultVO.fail(rest.isEmpty() ? "抢购失败" : rest);
    }
}
