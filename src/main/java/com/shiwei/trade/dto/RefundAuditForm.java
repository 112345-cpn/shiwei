package com.shiwei.trade.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefundAuditForm {

    @NotNull(message = "请选择退款单")
    private Long refundId;

    @NotNull(message = "请选择处理结果")
    private Boolean pass;

    @Size(max = 255, message = "备注最长 255 字")
    private String remark;
}
