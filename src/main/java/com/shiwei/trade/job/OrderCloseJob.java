package com.shiwei.trade.job;

import com.shiwei.trade.mapper.OrderMapper;
import com.shiwei.trade.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 超时关单兜底。
 * 主路径是延迟队列，这个任务是保险：MQ 挂了、消息投递失败、消费者异常，
 * 单子都会卡在待支付占着库存，只能靠扫表捞回来。
 * 每分钟一次，和 15 分钟的支付窗口相比余量足够。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCloseJob {

    private static final int BATCH = 200;

    private final OrderMapper orderMapper;
    private final OrderService orderService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void closeTimeoutOrders() {
        List<Long> ids = orderMapper.selectTimeoutIds(BATCH);
        if (ids.isEmpty()) {
            return;
        }
        int closed = 0;
        for (Long id : ids) {
            try {
                if (orderService.closeIfTimeout(id)) {
                    closed++;
                }
            } catch (Exception e) {
                log.error("兜底关单失败 orderId={}", id, e);
            }
        }
        log.info("兜底关单：扫到 {} 笔超时单，实际关闭 {} 笔", ids.size(), closed);
    }
}
