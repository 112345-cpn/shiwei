package com.shiwei.auth.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SendCodeForm {

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不对")
    private String phone;
}
