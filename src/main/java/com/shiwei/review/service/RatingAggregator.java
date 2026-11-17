package com.shiwei.review.service;

import com.shiwei.review.mapper.ReviewMapper;
import com.shiwei.review.vo.RatingStatRow;
import com.shiwei.shop.mapper.ShopMapper;
import com.shiwei.shop.mapper.ShopRatingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 门店评分聚合。
 *
 * 每次重算而不是增量加减，理由是：评价可以被审核通过、被隐藏、被删除，
 * 增量加減要处理六种状态迁移，容易算错。全量重算一条 SQL 就完事，
 * 一致性天然成立。代价是 O(该店评价数)，评价量上到万级要换成
 * 增量 + 定时校准。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RatingAggregator {

    private final ReviewMapper reviewMapper;
    private final ShopRatingMapper shopRatingMapper;
    private final ShopMapper shopMapper;

    @Transactional(rollbackFor = Exception.class)
    public void recalculate(Long shopId) {
        RatingStatRow stat = reviewMapper.selectStat(shopId);
        if (stat == null) {
            return;
        }

        stat.setAvgTotal(scale(stat.getAvgTotal()));
        stat.setAvgTaste(scale(stat.getAvgTaste()));
        stat.setAvgEnv(scale(stat.getAvgEnv()));
        stat.setAvgService(scale(stat.getAvgService()));

        shopRatingMapper.upsert(shopId, stat);
        shopMapper.updateRating(shopId, stat.getAvgTotal().setScale(1, RoundingMode.HALF_UP), stat.getReviewCount());

        log.debug("评分重算 shopId={} count={} avg={}", shopId, stat.getReviewCount(), stat.getAvgTotal());
    }

    private BigDecimal scale(BigDecimal v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
