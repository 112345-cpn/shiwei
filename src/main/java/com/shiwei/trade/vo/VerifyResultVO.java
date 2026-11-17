package com.shiwei.trade.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 核销结果。核销员扫完码要看到「这单是什么、能不能销」，
 * 所以除了结果还回带订单摘要，避免他再切页面确认。
 */
@Data
public class VerifyResultVO {

    private Long orderId;
    private String orderNo;
    private String title;
    private Long shopId;
    private String shopName;

    private Long userId;
    private String userNickName;

    private Integer quantity;
    private Integer payAmount;

    private LocalDateTime verifyTime;
    private LocalDateTime validEnd;

    /** true 表示这单之前已经核销过，是重复扫码而不是新核销 */
    private Boolean repeated;
}
