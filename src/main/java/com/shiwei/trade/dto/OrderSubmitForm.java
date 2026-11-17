package com.shiwei.trade.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderSubmitForm {

    @NotNull(message = "请选择券")
    private Long voucherId;

    @NotNull(message = "请填写购买数量")
    @Min(value = 1, message = "至少买 1 张")
    @Max(value = 5, message = "单次最多买 5 张")
    private Integer quantity;
}
