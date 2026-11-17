package com.shiwei.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalTime;

@Data
@TableName("tb_shop_hours")
public class ShopHour {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shopId;
    private Integer dayOfWeek;
    private LocalTime openTime;
    private LocalTime closeTime;
}
