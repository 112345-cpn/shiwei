package com.shiwei.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyForm {

    @NotBlank(message = "请填写核销码")
    @Size(max = 16, message = "核销码格式不对")
    private String verifyCode;
}
