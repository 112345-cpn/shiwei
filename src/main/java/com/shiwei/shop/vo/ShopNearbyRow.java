package com.shiwei.shop.vo;

import lombok.Data;

@Data
public class ShopNearbyRow {

    private Long id;
    private String name;
    private Long typeId;
    private String images;
    private String area;
    private String address;
    private Integer avgPrice;
    private java.math.BigDecimal rating;
    private Integer ratingCount;
    private Double x;
    private Double y;
    private Double distance;
}
