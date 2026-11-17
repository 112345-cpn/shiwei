package com.shiwei.trade.vo;

import lombok.Data;

/**
 * 秒杀结果。抢到不等于下单成功，所以要多一个 PENDING 态：
 * Redis 扣库存只是拿到资格，真正落库在 MQ 消费者里做。
 */
@Data
public class SeckillResultVO {

    public static final String PENDING = "PENDING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAIL = "FAIL";

    private String status;
    private Long orderId;
    private String message;

    public static SeckillResultVO pending() {
        SeckillResultVO vo = new SeckillResultVO();
        vo.setStatus(PENDING);
        vo.setMessage("排队中，稍后刷新看结果");
        return vo;
    }

    public static SeckillResultVO success(Long orderId) {
        SeckillResultVO vo = new SeckillResultVO();
        vo.setStatus(SUCCESS);
        vo.setOrderId(orderId);
        vo.setMessage("抢到了，去支付");
        return vo;
    }

    public static SeckillResultVO fail(String message) {
        SeckillResultVO vo = new SeckillResultVO();
        vo.setStatus(FAIL);
        vo.setMessage(message);
        return vo;
    }
}
