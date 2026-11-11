package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    Result createVoucherOrder(VoucherOrder voucherId);

    /**
     * 异步创建订单（MQ 消费者调用）
     */
    Result asyncCreateVoucherOrder(SeckillOrderMessage message);

    /**
     * 关闭超时未支付订单（死信队列消费者调用）
     */
    void closeOrder(Long orderId);
}
