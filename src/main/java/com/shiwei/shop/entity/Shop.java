package com.shiwei.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tb_shop")
public class Shop {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;
    private String name;
    private Long typeId;
    private String images;
    private String area;
    private String address;
    private Double x;
    private Double y;
    private Integer avgPrice;
    private Integer sold;
    private BigDecimal rating;
    private Integer ratingCount;

    /** 0 营业 1 歇业 2 下线 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
