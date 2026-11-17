package com.shiwei.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_voucher")
public class Voucher {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;
    private Long shopId;

    private String title;
    private String subTitle;
    private String rules;

    /** 0 代金券 1 套餐 2 单品 */
    private Integer type;

    private Integer originalPrice;
    private Integer salePrice;
    private Integer stock;
    private Integer sold;

    private LocalDateTime saleBegin;
    private LocalDateTime saleEnd;
    private LocalDateTime validBegin;
    private LocalDateTime validEnd;

    /** 0 草稿 1 上架 2 下架 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
