package com.shiwei.review.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 评分聚合的原始统计结果，直接由一条 SQL 算出，不做多次查询。
 */
@Data
public class RatingStatRow {

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
}
