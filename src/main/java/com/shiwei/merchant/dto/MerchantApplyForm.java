package com.shiwei.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MerchantApplyForm {

    @NotBlank(message = "商家名称不能为空")
    @Size(max = 64, message = "名称最长 64 个字符")
    private String name;

    @NotBlank(message = "营业执照号不能为空")
    @Size(max = 64, message = "执照号过长")
    private String licenseNo;

    @NotBlank(message = "请上传营业执照照片")
    private String licenseImg;

    @NotBlank(message = "联系人不能为空")
    private String contactName;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "联系电话格式不对")
    private String contactPhone;
}
