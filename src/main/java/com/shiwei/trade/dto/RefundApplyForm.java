package com.shiwei.trade.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefundApplyForm {

    @NotNull(message = "请选择订单")
    private Long orderId;

    @Size(max = 255, message = "退款原因最长 255 字")
    private String reason;
}
