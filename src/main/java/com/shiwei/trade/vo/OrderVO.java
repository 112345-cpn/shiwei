package com.shiwei.trade.vo;

import com.shiwei.common.enums.OrderStatus;
import com.shiwei.trade.entity.Order;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderVO {

    private Long id;
    private String orderNo;

    private Long voucherId;
    private Long shopId;
    private String shopName;

    /** 下单时的商品快照，券后面改价改名都不影响这里 */
    private String title;
    private Integer quantity;
    private Integer unitPrice;
    private Integer payAmount;

    private Integer status;
    private String statusDesc;

    private String verifyCode;
    private LocalDateTime payTime;
    private LocalDateTime verifyTime;
    private LocalDateTime expireTime;
    private LocalDateTime refundTime;
    private LocalDateTime createTime;

    /** 待支付 + 未超时 */
    private Boolean canPay;
    /** 待使用 */
    private Boolean canVerify;
    /** 待使用 + 未超可退期 */
    private Boolean canRefund;
    /** 已核销 + 没评过 */
    private Boolean canReview;

    /** 订单状态为「退款中 / 已退款」时，带上退款单状态给前端展示 */
    private Integer refundStatus;

    public static OrderVO of(Order o) {
        OrderVO vo = new OrderVO();
        vo.setId(o.getId());
        vo.setOrderNo(o.getOrderNo());
        vo.setVoucherId(o.getVoucherId());
        vo.setShopId(o.getShopId());
        vo.setTitle(o.getTitle());
        vo.setQuantity(o.getQuantity());
        vo.setUnitPrice(o.getUnitPrice());
        vo.setPayAmount(o.getPayAmount());
        vo.setStatus(o.getStatus());
        OrderStatus st = OrderStatus.of(o.getStatus());
        vo.setStatusDesc(st.getDesc());
        vo.setVerifyCode(o.getVerifyCode());
        vo.setPayTime(o.getPayTime());
        vo.setVerifyTime(o.getVerifyTime());
        vo.setExpireTime(o.getExpireTime());
        vo.setRefundTime(o.getRefundTime());
        vo.setCreateTime(o.getCreateTime());

        vo.setCanPay(st.canPay() && (o.getExpireTime() == null || o.getExpireTime().isAfter(LocalDateTime.now())));
        vo.setCanVerify(st.canVerify());
        vo.setCanRefund(st.canRefund());
        // canReview 要查评价表才知道，由 Service 补
        vo.setCanReview(false);
        return vo;
    }
}
