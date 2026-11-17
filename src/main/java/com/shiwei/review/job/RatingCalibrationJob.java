package com.shiwei.review.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shiwei.review.entity.Review;
import com.shiwei.review.mapper.ReviewMapper;
import com.shiwei.review.service.RatingAggregator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 每天凌晨把有评价的门店重算一遍。
 * 存在的意义：MQ 消费失败、消息丢失、手工改库之后，数据能自己走回来。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RatingCalibrationJob {

    private final ReviewMapper reviewMapper;
    private final RatingAggregator aggregator;

    @Scheduled(cron = "0 30 3 * * ?")
    public void calibrate() {
        List<Review> all = reviewMapper.selectList(Wrappers.<Review>lambdaQuery()
                .select(Review::getShopId)
                .eq(Review::getStatus, 1)
                .groupBy(Review::getShopId));

        log.info("评分校准开始，共 {} 家门店", all.size());
        int failed = 0;
        for (Review r : all) {
            try {
                aggregator.recalculate(r.getShopId());
            } catch (Exception e) {
                failed++;
                log.error("校准失败 shopId={}", r.getShopId(), e);
            }
        }
        log.info("评分校准结束，失败 {}", failed);
    }
}
