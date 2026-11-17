package com.shiwei.trade.mq;

import com.shiwei.common.config.RabbitMQConfig;
import com.shiwei.shop.mapper.ShopMapper;
import com.shiwei.trade.entity.Order;
import com.shiwei.trade.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidConsumer {

    private final OrderMapper orderMapper;
    private final ShopMapper shopMapper;

    /**
     * 支付成功后重算门店销量。
     * 重算是幂等的，重复投递只是多算一次；写销量不能放主链路里，
     * 它失败不该让用户付不了款。
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_ORDER_PAID)
    public void onPaid(Long orderId) {
        if (orderId == null) {
            return;
        }
        Order o = orderMapper.selectById(orderId);
        if (o == null || o.getShopId() == null) {
            return;
        }
        try {
            shopMapper.refreshSold(o.getShopId());
        } catch (Exception e) {
            log.error("门店销量重算失败 shopId={} orderId={}", o.getShopId(), orderId, e);
        }
    }
}
