package com.shiwei.trade.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商家建券 / 改券。
 * 价格字段单位是「分」，不是元 —— 全链路只用 int 存分，避免浮点误差。
 */
@Data
public class VoucherForm {

    @NotNull(message = "请选择门店")
    private Long shopId;

    @NotBlank(message = "请填写券名称")
    @Size(max = 64, message = "券名称最长 64 字")
    private String title;

    @Size(max = 128, message = "副标题最长 128 字")
    private String subTitle;

    @Size(max = 1000, message = "使用规则最长 1000 字")
    private String rules;

    /** 0 代金券 1 套餐 2 单品 */
    @NotNull(message = "请选择券类型")
    @Min(0)
    @Max(2)
    private Integer type;

    @NotNull(message = "请填写原价")
    @Min(value = 0, message = "原价不能为负")
    private Integer originalPrice;

    @NotNull(message = "请填写售价")
    @Min(value = 1, message = "售价至少 1 分")
    private Integer salePrice;

    @NotNull(message = "请填写库存")
    @Min(value = 0, message = "库存不能为负")
    private Integer stock;

    @NotNull(message = "请选择开售时间")
    private LocalDateTime saleBegin;

    @NotNull(message = "请选择停售时间")
    private LocalDateTime saleEnd;

    @NotNull(message = "请选择有效期开始")
    private LocalDateTime validBegin;

    @NotNull(message = "请选择有效期结束")
    private LocalDateTime validEnd;
}
