package com.shiwei.shop.vo;

import com.shiwei.shop.entity.Shop;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
public class ShopVO {

    private Long id;
    private Long merchantId;
    private String name;
    private Long typeId;
    private String typeName;
    private List<String> images = new ArrayList<>();
    private String cover;
    private String area;
    private String address;
    private Double x;
    private Double y;
    private Integer avgPrice;
    private Integer sold;
    private BigDecimal rating;
    private Integer ratingCount;
    private Integer status;
    /** 距离，单位米，只有附近查询会填 */
    private Double distance;
    private List<OpenHour> hours = new ArrayList<>();

    public static ShopVO of(Shop s) {
        ShopVO v = new ShopVO();
        v.id = s.getId();
        v.merchantId = s.getMerchantId();
        v.name = s.getName();
        v.typeId = s.getTypeId();
        v.images = splitImages(s.getImages());
        v.cover = v.images.isEmpty() ? "" : v.images.get(0);
        v.area = s.getArea();
        v.address = s.getAddress();
        v.x = s.getX();
        v.y = s.getY();
        v.avgPrice = s.getAvgPrice();
        v.sold = s.getSold();
        v.rating = s.getRating();
        v.ratingCount = s.getRatingCount();
        v.status = s.getStatus();
        return v;
    }

    public static ShopVO of(ShopNearbyRow r) {
        ShopVO v = new ShopVO();
        v.id = r.getId();
        v.name = r.getName();
        v.typeId = r.getTypeId();
        v.images = splitImages(r.getImages());
        v.cover = v.images.isEmpty() ? "" : v.images.get(0);
        v.area = r.getArea();
        v.address = r.getAddress();
        v.x = r.getX();
        v.y = r.getY();
        v.avgPrice = r.getAvgPrice();
        v.rating = r.getRating();
        v.ratingCount = r.getRatingCount();
        v.distance = r.getDistance();
        return v;
    }

    private static List<String> splitImages(String images) {
        if (images == null || images.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(images.split(",")));
    }

    @Data
    public static class OpenHour {
        private Integer dayOfWeek;
        private String openTime;
        private String closeTime;
    }
}
