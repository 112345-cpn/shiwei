package com.shiwei.merchant.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MerchantAuditForm {

    @NotNull(message = "商家 id 不能为空")
    private Long merchantId;

    @NotNull(message = "请选择审核结果")
    private Boolean pass;

    @Size(max = 255, message = "审核意见最长 255 个字符")
    private String remark;
}
