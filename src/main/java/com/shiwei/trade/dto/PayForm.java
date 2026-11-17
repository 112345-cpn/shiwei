package com.shiwei.trade.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class PayForm {

    /**
     * 0 余额 1 模拟支付 2 支付宝。
     * 当前只接了模拟支付：支付渠道是要和真实商户号、回调签名打交道的东西，
     * 接真渠道属于另一件事，这里用 channel 字段把扩展点先留出来。
     */
    @Min(0)
    @Max(2)
    private Integer channel;
}
