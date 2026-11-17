package com.shiwei.trade.vo;

import com.shiwei.trade.entity.Refund;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RefundVO {

    private Long id;
    private String refundNo;
    private Long orderId;
    private String orderNo;
    private String title;

    private Integer amount;
    private String reason;

    /** 0 待审 1 已同意 2 已退款 3 已驳回 */
    private Integer status;
    private String statusDesc;

    private LocalDateTime auditTime;
    private LocalDateTime finishTime;
    private LocalDateTime createTime;

    public static RefundVO of(Refund r) {
        RefundVO vo = new RefundVO();
        vo.setId(r.getId());
        vo.setRefundNo(r.getRefundNo());
        vo.setOrderId(r.getOrderId());
        vo.setAmount(r.getAmount());
        vo.setReason(r.getReason());
        vo.setStatus(r.getStatus());
        vo.setStatusDesc(statusDesc(r.getStatus()));
        vo.setAuditTime(r.getAuditTime());
        vo.setFinishTime(r.getFinishTime());
        vo.setCreateTime(r.getCreateTime());
        return vo;
    }

    public static String statusDesc(Integer status) {
        return switch (status == null ? 0 : status) {
            case 1 -> "商家已同意";
            case 2 -> "已退款";
            case 3 -> "已驳回";
            default -> "等待商家处理";
        };
    }
}
