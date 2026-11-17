package com.shiwei.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tb_shop_rating")
public class ShopRating {

    @TableId(type = IdType.INPUT)
    private Long shopId;

    private Integer reviewCount;
    private BigDecimal avgTotal;
    private BigDecimal avgTaste;
    private BigDecimal avgEnv;
    private BigDecimal avgService;
    private Integer star1;
    private Integer star2;
    private Integer star3;
    private Integer star4;
    private Integer star5;

    private LocalDateTime updateTime;
}
