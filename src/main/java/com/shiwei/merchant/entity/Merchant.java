package com.shiwei.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_merchant")
public class Merchant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ownerUserId;
    private String name;
    private String licenseNo;
    private String licenseImg;
    private String contactName;
    private String contactPhone;

    /** 0 待审 1 通过 2 驳回 */
    private Integer auditStatus;
    private String auditRemark;
    private LocalDateTime auditTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
