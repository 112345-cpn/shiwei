package com.shiwei.review.vo;

import com.shiwei.shop.entity.ShopRating;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 门店评价页头部的汇总：
 * 总分、三个分项、五档星级分布、正面/负面标签出现次数。
 */
@Data
public class ReviewSummaryVO {

    private Long shopId;
    private Integer reviewCount;
    private Double avgTotal;
    private Double avgTaste;
    private Double avgEnv;
    private Double avgService;
    /** 索引 0 对应 1 星，索引 4 对应 5 星 */
    private List<Integer> starDist = new ArrayList<>(5);
    private List<TagCount> topTags = new ArrayList<>();

    public static ReviewSummaryVO of(ShopRating r) {
        ReviewSummaryVO v = new ReviewSummaryVO();
        if (r == null) {
            v.reviewCount = 0;
            v.avgTotal = 0D;
            v.avgTaste = 0D;
            v.avgEnv = 0D;
            v.avgService = 0D;
            v.starDist = List.of(0, 0, 0, 0, 0);
            return v;
        }
        v.shopId = r.getShopId();
        v.reviewCount = r.getReviewCount();
        v.avgTotal = toDouble(r.getAvgTotal());
        v.avgTaste = toDouble(r.getAvgTaste());
        v.avgEnv = toDouble(r.getAvgEnv());
        v.avgService = toDouble(r.getAvgService());
        v.starDist = List.of(
                nvl(r.getStar1()), nvl(r.getStar2()), nvl(r.getStar3()), nvl(r.getStar4()), nvl(r.getStar5()));
        return v;
    }

    private static double toDouble(java.math.BigDecimal d) {
        return d == null ? 0D : d.doubleValue();
    }

    private static int nvl(Integer i) {
        return i == null ? 0 : i;
    }

    @Data
    public static class TagCount {
        private Long id;
        private String name;
        private Integer polarity;
        private Integer count;
    }
}
