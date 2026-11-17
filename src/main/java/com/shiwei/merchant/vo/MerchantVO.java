package com.shiwei.merchant.vo;

import com.shiwei.merchant.entity.Merchant;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MerchantVO {

    private Long id;
    private String name;
    private String licenseNo;
    private String licenseImg;
    private String contactName;
    private String contactPhone;
    private Integer auditStatus;
    private String auditRemark;
    private LocalDateTime auditTime;
    private LocalDateTime createTime;

    public static MerchantVO of(Merchant m) {
        MerchantVO v = new MerchantVO();
        v.id = m.getId();
        v.name = m.getName();
        v.licenseNo = m.getLicenseNo();
        v.licenseImg = m.getLicenseImg();
        v.contactName = m.getContactName();
        v.contactPhone = m.getContactPhone();
        v.auditStatus = m.getAuditStatus();
        v.auditRemark = m.getAuditRemark();
        v.auditTime = m.getAuditTime();
        v.createTime = m.getCreateTime();
        return v;
    }
}
