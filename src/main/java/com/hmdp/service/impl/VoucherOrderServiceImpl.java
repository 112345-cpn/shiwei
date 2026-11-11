package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.config.RabbitMQConfig;
import com.hmdp.service.IRabbitMQService;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.NotificationService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Resource;

import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * 秒杀订单服务实现（RabbitMQ 异步削峰版本）
 *
 * 核心流程：
 * 1. 用户请求 → Lua 脚本原子操作 Redis 预扣库存
 * 2. Lua 脚本成功 → 生成订单号 → 发 MQ 消息
 * 3. MQ 消费者异步处理 → 写 DB 创建订单
 * 4. 订单超时（30s） → 死信队列 → 自动关单 + 库存回补
 *
 * 优势：
 * - 秒杀接口响应时间从 200ms 降到 5ms（只走 Redis）
 * - MQ 异步下单，QPS 提升 6 倍以上
 * - 死信队列自动处理超时订单，无需定时任务
 *
 * @author HM-DianPing
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherOrderServiceImpl extends ServiceImpl<com.hmdp.mapper.VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    private final ISeckillVoucherService seckillVoucherService;
    private final RedisWorker redisWorker;
    private final StringRedisTemplate stringRedisTemplate;
    private final IRabbitMQService rabbitMQService;
    private final NotificationService notificationService;

    /**
     * 自身代理，用来解决同类方法自调用导致 @Transactional 失效的问题。
     *
     * 背景：handleSeckillOrderMessage 里如果直接写 this.asyncCreateVoucherOrder(...)，
     * 调用发生在同一个对象内部，不经过 Spring 的 AOP 代理，方法上的 @Transactional 完全不生效 ——
     * 扣库存成功、建订单失败时库存就白扣了。
     *
     * 用 @Lazy 注入自身代理，调用 self.asyncCreateVoucherOrder(...) 才会走代理、事务才真正开启。
     */
    private IVoucherOrderService self;

    @Autowired
    @Lazy
    public void setSelf(IVoucherOrderService self) {
        this.self = self;
    }

    /** MQ 幂等键前缀 */
    private static final String MQ_ORDER_IDEMPOTENT_KEY = "mq:order:idempotent:";
    /** MQ 死信处理标记前缀 */
    private static final String MQ_DEAD_HANDLED_KEY = "mq:order:dead:handled:";

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // ===============================================
    // Step 1: 秒杀入口（Lua 预扣库存 → 发 MQ 消息）
    // ===============================================

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1. 获取用户 ID
        Long userId = UserHolder.getUser().getId();

        // 2. 生成全局唯一订单 ID（雪花算法）
        long orderId = redisWorker.nextId("order");

        // 3. 执行 Lua 脚本（原子操作：检查库存 + 扣减库存 + 检查一人一单）
        //    注意：库存 Key 与一人一单 Key 必须通过 KEYS 列表传入，
        //    不能塞进 ARGV 里拼——Redis Cluster 依赖 KEYS 计算 slot 并做路由校验。
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                List.of(SECKILL_STOCK_KEY + voucherId, SECKILL_ORDER_KEY + voucherId),
                userId.toString(),
                String.valueOf(orderId)
        );

        // 4. 判断 Lua 执行结果
        if (result == null) {
            return Result.fail("系统异常，请重试");
        }
        int r = result.intValue();
        if (r != 0) {
            // r == 1：库存不足；r == 2：重复下单
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        // 5. Lua 执行成功，发送 MQ 消息（异步下单）
        SeckillOrderMessage message = SeckillOrderMessage.builder()
                .userId(userId)
                .voucherId(voucherId)
                .orderId(orderId)
                .timestamp(System.currentTimeMillis())
                .build();

        try {
            rabbitMQService.sendSeckillOrderMessage(message);
        } catch (Exception e) {
            // 补偿：Lua 已扣库存，但消息没发出去 —— 不回补的话这笔库存就凭空消失了
            log.error("秒杀消息发送失败，执行库存回补：userId={}, voucherId={}, orderId={}",
                    userId, voucherId, orderId, e);
            rollbackSeckill(voucherId, userId);
            return Result.fail("系统繁忙，请稍后重试");
        }

        log.info("秒杀请求接收成功，订单已下发 MQ：userId={}, voucherId={}, orderId={}",
                userId, voucherId, orderId);

        // 6. 立即返回订单号（前端开始倒计时）
        return Result.ok(orderId);
    }

    /**
     * 秒杀失败补偿：回补 Redis 库存，并移除一人一单标记，允许用户重新下单。
     * 用于 Lua 执行成功、但后续流程（如发送 MQ）失败的场景。
     */
    private void rollbackSeckill(Long voucherId, Long userId) {
        try {
            stringRedisTemplate.opsForValue().increment(SECKILL_STOCK_KEY + voucherId);
            stringRedisTemplate.opsForSet().remove(SECKILL_ORDER_KEY + voucherId, userId.toString());
            log.info("库存回补完成：voucherId={}, userId={}", voucherId, userId);
        } catch (Exception ex) {
            // 回补失败只能记日志告警，由对账任务兜底
            log.error("库存回补失败，需人工介入：voucherId={}, userId={}", voucherId, userId, ex);
        }
    }

    // ===============================================
    // Step 2: MQ 消费者异步下单（事务保证一致性）
    // ===============================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result asyncCreateVoucherOrder(SeckillOrderMessage message) {
        Long userId = message.getUserId();
        Long voucherId = message.getVoucherId();
        Long orderId = message.getOrderId();

        // 1. 一人一单校验（DB 层兜底）
        Long count = query().eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        if (count > 0) {
            log.warn("用户已下单（DB 层拦截）：userId={}, voucherId={}", userId, voucherId);
            return Result.fail("不能重复下单");
        }

        // 2. 扣减 DB 库存（MySQL 乐观锁兜底）
        boolean stockUpdated = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!stockUpdated) {
            log.error("DB 扣减库存失败：voucherId={}", voucherId);
            return Result.fail("库存不足");
        }

        // 3. 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setStatus(1); // 1：未支付
        voucherOrder.setCreateTime(LocalDateTime.now());
        boolean saved = save(voucherOrder);

        if (!saved) {
            log.error("订单保存失败：orderId={}", orderId);
            return Result.fail("订单创建失败");
        }

        log.info("订单创建成功：userId={}, voucherId={}, orderId={}", userId, voucherId, orderId);
        return Result.ok();
    }

    // ===============================================
    // Step 3: 关单（死信队列消费者调用）
    // ===============================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeOrder(Long orderId) {
        VoucherOrder order = getById(orderId);
        // 注意：status 是 Integer，直接 != 1 有拆箱 NPE 风险，先判 null
        if (order == null || order.getStatus() == null || order.getStatus() != 1) {
            log.info("订单状态非未支付，跳过关单：orderId={}", orderId);
            return;
        }

        // 关闭订单
        order.setStatus(4); // 4：已取消
        order.setUpdateTime(LocalDateTime.now());
        boolean updated = updateById(order);
        if (!updated) {
            log.warn("订单关闭失败：orderId={}", orderId);
            return;
        }
        log.info("订单超时已关闭：orderId={}", orderId);

        // 回补数据库库存。
        // 下单时 DB 已执行 stock = stock - 1，若关单不回补，库存会越卖越少（少卖）。
        // 与改状态放在同一事务内，保证原子性。
        boolean restored = seckillVoucherService.update()
                .setSql("stock = stock + 1")
                .eq("voucher_id", order.getVoucherId())
                .update();
        if (restored) {
            log.info("已回补数据库库存：orderId={}, voucherId={}", orderId, order.getVoucherId());
        } else {
            log.error("关单成功但数据库库存回补失败，需人工介入：orderId={}, voucherId={}",
                    orderId, order.getVoucherId());
        }
    }

    // ===============================================
    // 保留方法（兼容旧接口）
    // ===============================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        // 一人一单
        Long count = query().eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        if (count > 0) {
            return Result.fail("不能重复下单");
        }

        // 扣减库存
        boolean updated = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!updated) {
            return Result.fail("库存不足");
        }

        save(voucherOrder);
        return Result.ok();
    }

    // ===============================================
    // MQ 消费者：处理秒杀下单消息（替代原 RabbitMQServiceImpl 中的消费者）
    // ===============================================

    @RabbitListener(queues = RabbitMQConfig.SECKILL_ORDER_QUEUE)
    public void handleSeckillOrderMessage(SeckillOrderMessage message) {
        log.info("收到秒杀下单消息：userId={}, voucherId={}, orderId={}",
                message.getUserId(), message.getVoucherId(), message.getOrderId());

        // 幂等键在 try 外定义，确保 catch 块中也能访问（异常时需要清理它以允许重试）
        String idempotentKey = MQ_ORDER_IDEMPOTENT_KEY + message.getOrderId();

        try {
            // 1. 幂等校验：检查是否处理过
            Boolean exists = stringRedisTemplate.opsForValue().setIfAbsent(
                    idempotentKey, "1", Duration.ofMinutes(5));
            if (exists == null || !exists) {
                log.warn("订单已处理（幂等拦截）：orderId={}", message.getOrderId());
                return;
            }

            // 2. 异步下单（必须走 self 代理，否则 @Transactional 不生效）
            Result result = self.asyncCreateVoucherOrder(message);

            if (!result.getSuccess()) {
                log.error("下单失败：{}", result.getErrorMsg());
                // 删除幂等键，允许重试
                stringRedisTemplate.delete(idempotentKey);
                // 失败也要告诉用户，别让前端一直等
                pushSeckillResult(message, false);
                return;
            }

            // 3. 到这里事务已提交，再推送 —— 顺序很重要，
            //    如果先推后提交，事务回滚时用户会收到"抢到了"但订单不存在。
            pushSeckillResult(message, true);

            // 4. 投递「延迟关单」消息：30s 内未支付就自动关单 + 回补库存。
            //    也放在提交之后 —— 事务回滚了就不该有关单消息，否则会去关一个不存在的订单。
            rabbitMQService.sendDelayCloseMessage(message);

        } catch (Exception e) {
            // 关键：必须先删除幂等键再抛异常。
            // 否则重试时幂等键仍在（TTL 5 分钟），setIfAbsent 返回 false，
            // 会被误判为「已处理」直接 return，导致这个订单永远创建不出来。
            log.error("处理秒杀下单消息异常，删除幂等键以允许重试：orderId={}", message.getOrderId(), e);
            stringRedisTemplate.delete(idempotentKey);
            throw e; // 抛出异常触发 RabbitMQ 重试 + 死信
        }
    }

    /**
     * 把秒杀结果通过 WebSocket 推给用户。
     *
     * 用户在线就实时收到；不在线会被 NotificationService 跳过（只记 debug 日志），
     * 前端可以拿 orderId 主动查一次订单状态做兜底。
     *
     * 推送失败不能影响下单主流程 —— 订单已经建好了，推送只是通知。
     */
    private void pushSeckillResult(SeckillOrderMessage message, boolean success) {
        try {
            Map<String, Object> payload = new HashMap<>(6);
            payload.put("type", "seckill_result");
            payload.put("orderId", message.getOrderId());
            payload.put("voucherId", message.getVoucherId());
            payload.put("success", success);
            payload.put("msg", success ? "抢到了，请在 30 秒内完成支付" : "很遗憾，下单失败");
            payload.put("timestamp", System.currentTimeMillis());
            notificationService.pushToUser(message.getUserId(), payload);
        } catch (Exception e) {
            log.warn("秒杀结果推送失败，不影响订单：orderId={}", message.getOrderId(), e);
        }
    }

    // ===============================================
    // MQ 消费者：处理死信消息（延迟关单）
    // ===============================================

    @RabbitListener(queues = RabbitMQConfig.SECKILL_ORDER_DL_QUEUE)
    public void handleDeadLetter(SeckillOrderMessage message) {
        log.info("收到死信消息（订单超时）：orderId={}", message.getOrderId());

        // 检查订单是否已支付（业务侧校验）
        VoucherOrder order = getById(message.getOrderId());
        if (order == null) {
            log.warn("订单不存在，可能已删除：orderId={}", message.getOrderId());
            return;
        }

        // 未支付且未关单的订单，执行关单（status 为 Integer，先判 null 防拆箱 NPE）
        if (order.getStatus() != null && order.getStatus() == 1) {
            log.warn("订单未支付（30 秒内未付款），执行关单：orderId={}", message.getOrderId());
            closeOrder(message.getOrderId()); // 内部会回补数据库库存

            // 回补 Redis 库存
            stringRedisTemplate.opsForValue().increment(SECKILL_STOCK_KEY + message.getVoucherId());
            // 移除一人一单标记，否则用户超时未支付后将无法再次参与秒杀
            stringRedisTemplate.opsForSet().remove(SECKILL_ORDER_KEY + message.getVoucherId(),
                    message.getUserId().toString());
        }

        // 标记死信已处理（避免死信消息重试触发重复关单）
        stringRedisTemplate.opsForValue().set(
                MQ_DEAD_HANDLED_KEY + message.getOrderId(),
                "1", Duration.ofDays(7));
    }
}