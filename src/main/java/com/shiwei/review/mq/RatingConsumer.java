package com.shiwei.review.mq;

import com.shiwei.common.config.RabbitMQConfig;
import com.shiwei.review.service.RatingAggregator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RatingConsumer {

    private final RatingAggregator aggregator;

    /**
     * 评价有任何状态变化都会投一条，这里全量重算该店的聚合。
     * 重算幂等，重复投递只是多算一次，不会算错。
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_RATING)
    public void onRatingChanged(Long shopId) {
        if (shopId == null) {
            return;
        }
        try {
            aggregator.recalculate(shopId);
        } catch (Exception e) {
            log.error("评分聚合失败 shopId={}", shopId, e);
            // 不抛出：抛出会让消息回到队列无限重投，坏掉一条评价拖垮整个队列
            // 兜底靠定时校准任务
        }
    }
}
