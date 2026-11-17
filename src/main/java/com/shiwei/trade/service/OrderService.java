package com.shiwei.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiwei.common.config.RabbitMQConfig;
import com.shiwei.common.enums.OrderStatus;
import com.shiwei.common.exception.BizException;
import com.shiwei.common.exception.ErrorCode;
import com.shiwei.common.result.PageResult;
import com.shiwei.common.util.IdGenerator;
import com.shiwei.review.entity.Review;
import com.shiwei.review.mapper.ReviewMapper;
import com.shiwei.shop.entity.Shop;
import com.shiwei.shop.mapper.ShopMapper;
import com.shiwei.trade.dto.OrderSubmitForm;
import com.shiwei.trade.dto.PayForm;
import com.shiwei.trade.entity.Order;
import com.shiwei.trade.entity.Payment;
import com.shiwei.trade.entity.Voucher;
import com.shiwei.trade.mapper.OrderMapper;
import com.shiwei.trade.mapper.PaymentMapper;
import com.shiwei.trade.mapper.VoucherMapper;
import com.shiwei.trade.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    /** 同一用户对同一张券，最多允许挂 3 笔待支付单，防脚本刷单占库存 */
    private static final int MAX_PENDING = 3;

    private static final int VERIFY_CODE_RETRY = 3;

    private final OrderMapper orderMapper;
    private final VoucherMapper voucherMapper;
    private final PaymentMapper paymentMapper;
    private final ShopMapper shopMapper;
    private final VoucherService voucherService;
    private final ReviewMapper reviewMapper;
    private final IdGenerator idGenerator;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 下单。库存扣减和订单落库在同一个事务里：
     * 库存扣成功但订单没落库，库存就永久少了一格，只能靠对账发现。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long submit(Long userId, OrderSubmitForm form) {
        Voucher v = voucherService.requireOnSale(form.getVoucherId());
        Shop shop = shopMapper.selectById(v.getShopId());
        if (shop == null) {
            throw BizException.of(ErrorCode.SHOP_NOT_FOUND);
        }
        if (shop.getStatus() != null && shop.getStatus() == 2) {
            throw BizException.of(ErrorCode.SHOP_OFFLINE);
        }

        Long pending = orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .eq(Order::getUserId, userId)
                .eq(Order::getVoucherId, v.getId())
                .eq(Order::getStatus, OrderStatus.PENDING_PAY.getCode()));
        if (pending != null && pending >= MAX_PENDING) {
            throw BizException.of(ErrorCode.VOUCHER_LIMIT_EXCEEDED, "你有太多未支付的订单，先处理一下");
        }

        int n = form.getQuantity();
        if (voucherMapper.deductStock(v.getId(), n) == 0) {
            throw BizException.of(ErrorCode.VOUCHER_SOLD_OUT);
        }

        Order o = new Order();
        o.setOrderNo(idGenerator.nextOrderNo());
        o.setUserId(userId);
        o.setVoucherId(v.getId());
        o.setShopId(v.getShopId());
        o.setMerchantId(v.getMerchantId());
        o.setTitle(v.getTitle());
        o.setQuantity(n);
        o.setUnitPrice(v.getSalePrice());
        // 金额用乘出来的结果落快照，不依赖前端传的合计，避免被改价下单
        o.setPayAmount(v.getSalePrice() * n);
        o.setStatus(OrderStatus.PENDING_PAY.getCode());
        // 和延迟队列的 TTL 用同一个常量，否则消息先到或晚到会让关单时间对不上
        o.setExpireTime(LocalDateTime.now().plus(Duration.ofMillis(RabbitMQConfig.ORDER_PAY_TTL_MS)));

        insertWithVerifyCode(o);
        sendDelayClose(o.getId());
        return o.getId();
    }

    /**
     * 支付。渠道目前只有模拟支付，channel 字段先留扩展位。
     * 关键在 markPaid 这一步：条件更新 status=0，并发下只有一笔能成功，
     * 靠它把「重复支付」挡在流水表之前。
     */
    @Transactional(rollbackFor = Exception.class)
    public void pay(Long userId, Long orderId, PayForm form) {
        Order o = orderMapper.selectById(orderId);
        if (o == null) {
            throw BizException.of(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!o.getUserId().equals(userId)) {
            throw BizException.of(ErrorCode.ORDER_NOT_YOURS);
        }
        if (o.getStatus() != null && o.getStatus() == OrderStatus.PENDING_USE.getCode()) {
            throw BizException.of(ErrorCode.ORDER_ALREADY_PAID);
        }
        if (o.getStatus() == null || o.getStatus() != OrderStatus.PENDING_PAY.getCode()) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ILLEGAL);
        }
        if (o.getExpireTime() != null && o.getExpireTime().isBefore(LocalDateTime.now())) {
            // 顺手把这单关掉，不等延迟消息
            closeIfTimeout(orderId);
            throw BizException.of(ErrorCode.ORDER_EXPIRED);
        }

        LocalDateTime now = LocalDateTime.now();
        if (orderMapper.markPaid(orderId, now) == 0) {
            throw BizException.of(ErrorCode.ORDER_ALREADY_PAID);
        }

        Payment p = new Payment();
        p.setPayNo(idGenerator.nextPayNo());
        p.setOrderId(orderId);
        p.setChannel(form == null || form.getChannel() == null ? 1 : form.getChannel());
        p.setAmount(o.getPayAmount());
        p.setStatus(1);
        p.setExternalNo("MOCK" + System.currentTimeMillis());
        p.setNotifyTime(now);
        try {
            paymentMapper.insert(p);
        } catch (DuplicateKeyException e) {
            // uk_order 兜住：一单只能有一条支付流水
            throw BizException.of(ErrorCode.ORDER_ALREADY_PAID);
        }

        publishPaid(orderId);
    }

    /**
     * 超时关单。幂等：只有 status 还是 0 的单会被改，改成功才回滚库存。
     * 延迟消息和定时任务都会调到这里，重复调不会多退库存。
     *
     * @return true 表示这次真的关掉了
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean closeIfTimeout(Long orderId) {
        Order o = orderMapper.selectById(orderId);
        if (o == null || o.getStatus() == null || o.getStatus() != OrderStatus.PENDING_PAY.getCode()) {
            return false;
        }
        if (orderMapper.markClosed(orderId) == 0) {
            return false;
        }
        voucherMapper.restoreStock(o.getVoucherId(), o.getQuantity());
        return true;
    }

    public PageResult<OrderVO> myOrders(Long userId, Integer status, long pageNo, long pageSize) {
        IPage<Order> page = orderMapper.selectPage(new Page<>(pageNo, pageSize),
                Wrappers.<Order>lambdaQuery()
                        .eq(Order::getUserId, userId)
                        .eq(status != null, Order::getStatus, status)
                        .orderByDesc(Order::getId));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                assemble(page.getRecords(), userId));
    }

    public PageResult<OrderVO> merchantOrders(Long merchantId, Integer status, long pageNo, long pageSize) {
        IPage<Order> page = orderMapper.selectPage(new Page<>(pageNo, pageSize),
                Wrappers.<Order>lambdaQuery()
                        .eq(Order::getMerchantId, merchantId)
                        .eq(status != null, Order::getStatus, status)
                        .orderByDesc(Order::getId));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                assemble(page.getRecords(), null));
    }

    public OrderVO detail(Long userId, Long orderId) {
        Order o = orderMapper.selectById(orderId);
        if (o == null) {
            throw BizException.of(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!o.getUserId().equals(userId)) {
            throw BizException.of(ErrorCode.ORDER_NOT_YOURS);
        }
        return assemble(List.of(o), userId).get(0);
    }

    /** 给退款、核销用的取值 + 归属校验，只查一次库。 */
    public Order requireMine(Long userId, Long orderId) {
        Order o = orderMapper.selectById(orderId);
        if (o == null) {
            throw BizException.of(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!o.getUserId().equals(userId)) {
            throw BizException.of(ErrorCode.ORDER_NOT_YOURS);
        }
        return o;
    }

    public Order requireByMerchant(Long merchantId, Long orderId) {
        Order o = orderMapper.selectById(orderId);
        if (o == null) {
            throw BizException.of(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!o.getMerchantId().equals(merchantId)) {
            throw BizException.of(ErrorCode.FORBIDDEN, "这单不属于你的门店");
        }
        return o;
    }

    /**
     * 核销码是 6 位数字，100 万空间里随机会撞。
     * 靠 uk_verify_code 兜住，撞了就换一个重试，不靠「先查有没有重复」——
     * 那样查完到插入之间仍然有窗口。
     */
    private void insertWithVerifyCode(Order o) {
        for (int i = 0; i < VERIFY_CODE_RETRY; i++) {
            o.setVerifyCode(String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000)));
            try {
                orderMapper.insert(o);
                return;
            } catch (DuplicateKeyException e) {
                if (i == VERIFY_CODE_RETRY - 1) {
                    throw BizException.of(ErrorCode.INTERNAL_ERROR, "核销码生成冲突，请重试");
                }
            }
        }
    }

    private void sendDelayClose(Long orderId) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_DELAY, RabbitMQConfig.RK_ORDER_TIMEOUT, orderId);
        } catch (Exception e) {
            // 消息丢了不会让单永远挂着：定时任务每分钟扫一遍超时单兜底
            log.warn("延迟关单消息投递失败，等定时任务兜底 orderId={}", orderId, e);
        }
    }

    private void publishPaid(Long orderId) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_TRADE, RabbitMQConfig.RK_ORDER_PAID, orderId);
        } catch (Exception e) {
            log.warn("支付成功消息投递失败，门店销量会滞后 orderId={}", orderId, e);
        }
    }

    /** 补门店名 + 「这单评过没」。订单列表里带这个，前端才知道要不要给「去评价」按钮。 */
    private List<OrderVO> assemble(List<Order> orders, Long currentUserId) {
        if (orders.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> shopIds = orders.stream().map(Order::getShopId).distinct().toList();
        Map<Long, Shop> shops = shopMapper.selectBatchIds(shopIds).stream()
                .collect(Collectors.toMap(Shop::getId, Function.identity(), (a, b) -> a));

        Set<Long> reviewed = currentUserId == null ? Collections.emptySet()
                : reviewMapper.selectList(Wrappers.<Review>lambdaQuery()
                        .select(Review::getOrderId)
                        .eq(Review::getUserId, currentUserId)
                        .in(Review::getOrderId, orders.stream().map(Order::getId).toList()))
                .stream().map(Review::getOrderId).collect(Collectors.toSet());

        return orders.stream().map(o -> {
            OrderVO vo = OrderVO.of(o);
            Shop s = shops.get(o.getShopId());
            vo.setShopName(s == null ? "" : s.getName());
            boolean verified = o.getStatus() != null && o.getStatus() == OrderStatus.VERIFIED.getCode();
            vo.setCanReview(verified && !reviewed.contains(o.getId()));
            return vo;
        }).collect(Collectors.toCollection(ArrayList::new));
    }
}
