package com.shiwei.trade.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiwei.common.enums.OrderStatus;
import com.shiwei.common.exception.BizException;
import com.shiwei.common.exception.ErrorCode;
import com.shiwei.common.result.PageResult;
import com.shiwei.common.push.PushService;
import com.shiwei.common.push.PushType;
import com.shiwei.common.util.IdGenerator;
import com.shiwei.trade.dto.RefundApplyForm;
import com.shiwei.trade.dto.RefundAuditForm;
import com.shiwei.trade.entity.Order;
import com.shiwei.trade.entity.Payment;
import com.shiwei.trade.entity.Refund;
import com.shiwei.trade.entity.Voucher;
import com.shiwei.trade.mapper.OrderMapper;
import com.shiwei.trade.mapper.PaymentMapper;
import com.shiwei.trade.mapper.RefundMapper;
import com.shiwei.trade.mapper.SeckillVoucherMapper;
import com.shiwei.trade.mapper.VoucherMapper;
import com.shiwei.trade.vo.RefundVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 退款。未核销的券可以退，核销过的不能退 —— 这条规则是线下核销的常识，
 * 也让「先核销再退款」这种白嫖路径直接不存在。
 *
 * 审核通过后：退款单 → 已退款、订单 → 已退款、支付流水 → 已退、券库存回滚。
 * 这四步必须同一事务，少一步就会出「钱退了但库存没回来」或者反过来。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private static final int PENDING = 0;
    private static final int REFUNDED = 2;
    private static final int REJECTED = 3;

    private final RefundMapper refundMapper;
    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final VoucherMapper voucherMapper;
    private final SeckillVoucherMapper seckillVoucherMapper;
    private final SeckillQuotaStore quotaStore;
    private final OrderService orderService;
    private final IdGenerator idGenerator;
    private final PushService pushService;

    @Transactional(rollbackFor = Exception.class)
    public Long apply(Long userId, RefundApplyForm form) {
        Order o = orderService.requireMine(userId, form.getOrderId());

        if (o.getStatus() != null && o.getStatus() == OrderStatus.VERIFIED.getCode()) {
            throw BizException.of(ErrorCode.ORDER_ALREADY_VERIFIED, "已核销的订单不能退款");
        }
        if (o.getStatus() == null || o.getStatus() != OrderStatus.PENDING_USE.getCode()) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ILLEGAL);
        }

        Voucher v = voucherMapper.selectById(o.getVoucherId());
        if (v != null && v.getValidEnd() != null && v.getValidEnd().isBefore(LocalDateTime.now())) {
            throw BizException.of(ErrorCode.ORDER_REFUND_WINDOW_CLOSED);
        }

        Long pending = refundMapper.selectCount(Wrappers.<Refund>lambdaQuery()
                .eq(Refund::getOrderId, o.getId())
                .eq(Refund::getStatus, PENDING));
        if (pending != null && pending > 0) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "这一单已经在退款处理中了");
        }

        // 先把订单切到「退款中」再建单：切成功说明没有别人同时在申请
        if (orderMapper.markRefunding(o.getId()) == 0) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ILLEGAL);
        }

        Payment p = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderId, o.getId())
                .last("limit 1"));

        Refund r = new Refund();
        r.setRefundNo(idGenerator.nextRefundNo());
        r.setOrderId(o.getId());
        r.setPaymentId(p == null ? 0L : p.getId());
        r.setAmount(o.getPayAmount());
        r.setReason(form.getReason() == null ? "" : form.getReason());
        r.setStatus(PENDING);
        refundMapper.insert(r);
        return r.getId();
    }

    /**
     * 商家审核。通过即完成退款（没有真实渠道，所以不存在「等待渠道回调」这个中间态）；
     * 驳回则把订单放回待使用，用户可以继续用券。
     */
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long merchantId, Long staffUserId, RefundAuditForm form) {
        Refund r = refundMapper.selectById(form.getRefundId());
        if (r == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "退款单不存在");
        }
        Order o = orderService.requireByMerchant(merchantId, r.getOrderId());
        if (r.getStatus() == null || r.getStatus() != PENDING) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "这条退款申请已经处理过了");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean pass = Boolean.TRUE.equals(form.getPass());

        int rows = refundMapper.markAudited(r.getId(), pass ? REFUNDED : REJECTED,
                staffUserId, now, pass ? now : null);
        if (rows == 0) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "这条退款申请已经处理过了");
        }

        if (!pass) {
            orderMapper.rollbackToPendingUse(o.getId());
            pushService.toUser(o.getUserId(), PushType.REFUND_CHANGED,
                    Map.of("orderId", o.getId(), "status", "REJECTED"));
            return;
        }

        if (orderMapper.markRefunded(o.getId(), now) == 0) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ILLEGAL);
        }
        paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                .eq(Payment::getOrderId, o.getId())
                .set(Payment::getStatus, 3));
        voucherMapper.restoreStock(o.getVoucherId(), o.getQuantity());

        // 秒杀券退款后要把名额还回 Redis，否则他退了也抢不了第二次
        if (seckillVoucherMapper.selectById(o.getVoucherId()) != null) {
            quotaStore.releaseAfterCommit(o.getVoucherId(), o.getUserId());
        }

        pushService.toUser(o.getUserId(), PushType.REFUND_CHANGED,
                Map.of("orderId", o.getId(), "status", "REFUNDED", "amount", o.getPayAmount()));
    }

    public PageResult<RefundVO> myRefunds(Long userId, long pageNo, long pageSize) {
        IPage<Refund> page = refundMapper.pageByUser(new Page<>(pageNo, pageSize), userId);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), assemble(page.getRecords()));
    }

    public PageResult<RefundVO> merchantRefunds(Long merchantId, Integer status, long pageNo, long pageSize) {
        IPage<Refund> page = refundMapper.pageByMerchant(new Page<>(pageNo, pageSize), merchantId, status);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), assemble(page.getRecords()));
    }

    public long pendingCount(Long merchantId) {
        return refundMapper.countPending(merchantId);
    }

    private List<RefundVO> assemble(List<Refund> refunds) {
        if (refunds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> orderIds = refunds.stream().map(Refund::getOrderId).toList();
        List<Order> orders = orderMapper.selectBatchIds(orderIds);
        List<RefundVO> list = new ArrayList<>(refunds.size());
        for (Refund r : refunds) {
            RefundVO vo = RefundVO.of(r);
            orders.stream()
                    .filter(o -> o.getId().equals(r.getOrderId()))
                    .findFirst()
                    .ifPresent(o -> {
                        vo.setOrderNo(o.getOrderNo());
                        vo.setTitle(o.getTitle());
                    });
            list.add(vo);
        }
        return list;
    }
}
