package com.shiwei.trade.mq;

import com.shiwei.common.config.RabbitMQConfig;
import com.shiwei.common.exception.BizException;
import com.shiwei.trade.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private final SeckillService seckillService;

    /**
     * 秒杀落单。这里是整条链路的终点，也是最容易出不一致的地方：
     * Redis 已经扣过库存，数据库这一步失败就必须把 Redis 还回去，
     * 否则库存越抢越少，最后「明明还有货但谁也抢不到」。
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_SECKILL)
    public void onSeckill(SeckillMessage msg) {
        if (msg == null || msg.getVoucherId() == null || msg.getUserId() == null) {
            return;
        }
        Long voucherId = msg.getVoucherId();
        Long userId = msg.getUserId();

        try {
            Long orderId = seckillService.createOrder(msg);
            seckillService.markSuccess(voucherId, userId, orderId);
            log.info("秒杀落单成功 voucherId={} userId={} orderId={}", voucherId, userId, orderId);
        } catch (Exception e) {
            String reason = e instanceof BizException ? e.getMessage() : "下单失败，已为你退回资格";
            log.error("秒杀落单失败 voucherId={} userId={}", voucherId, userId, e);
            try {
                // rollback 会清掉结果 key，所以必须放在 markFail 之前
                seckillService.rollback(voucherId, userId);
                seckillService.markFail(voucherId, userId, reason);
            } catch (Exception inner) {
                // 连回滚都失败，只能靠预热任务和人工对账收拾
                log.error("秒杀回滚失败，库存可能少卖 voucherId={} userId={}", voucherId, userId, inner);
            }
        }
    }
}
