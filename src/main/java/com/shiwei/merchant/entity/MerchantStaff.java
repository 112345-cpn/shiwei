package com.shiwei.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_merchant_staff")
public class MerchantStaff {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;
    private Long userId;

    /** 0 法人 1 运营 2 核销员 */
    private Integer staffRole;

    /** 限定门店，0 表示全部门店 */
    private Long shopId;

    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
