package com.shiwei.trade.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shiwei.auth.entity.User;
import com.shiwei.auth.mapper.UserMapper;
import com.shiwei.common.enums.OrderStatus;
import com.shiwei.common.exception.BizException;
import com.shiwei.common.exception.ErrorCode;
import com.shiwei.merchant.entity.MerchantStaff;
import com.shiwei.merchant.mapper.MerchantStaffMapper;
import com.shiwei.merchant.service.MerchantService;
import com.shiwei.shop.entity.Shop;
import com.shiwei.shop.mapper.ShopMapper;
import com.shiwei.trade.dto.VerifyForm;
import com.shiwei.trade.entity.Order;
import com.shiwei.trade.entity.VerifyRecord;
import com.shiwei.trade.entity.Voucher;
import com.shiwei.trade.mapper.OrderMapper;
import com.shiwei.trade.mapper.VerifyRecordMapper;
import com.shiwei.trade.mapper.VoucherMapper;
import com.shiwei.trade.vo.VerifyResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyService {

    private final OrderMapper orderMapper;
    private final VoucherMapper voucherMapper;
    private final VerifyRecordMapper verifyRecordMapper;
    private final MerchantStaffMapper staffMapper;
    private final MerchantService merchantService;
    private final ShopMapper shopMapper;
    private final UserMapper userMapper;

    /**
     * 核销。四道门：
     * 1. 你是商家的人
     * 2. 这单属于你的商家，且在你被授权的门店内
     * 3. 单子处于待使用
     * 4. 券还在有效期内
     *
     * 重复扫码不报错，返回 repeated=true 让收银端提示「已于 xx 核销」——
     * 客人拿同一张码再扫一次是常见场景，报错会让店员误以为系统坏了。
     */
    @Transactional(rollbackFor = Exception.class)
    public VerifyResultVO verify(Long staffUserId, VerifyForm form) {
        Long merchantId = merchantService.merchantIdOf(staffUserId);
        if (merchantId == null) {
            throw BizException.of(ErrorCode.MERCHANT_NOT_OWNER);
        }

        Order o = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getVerifyCode, form.getVerifyCode().trim())
                .last("limit 1"));
        if (o == null) {
            throw BizException.of(ErrorCode.VERIFY_CODE_INVALID);
        }
        if (!o.getMerchantId().equals(merchantId)) {
            throw BizException.of(ErrorCode.STAFF_NOT_IN_SHOP);
        }
        requireShopAuthority(staffUserId, merchantId, o.getShopId());

        if (o.getStatus() != null && o.getStatus() == OrderStatus.VERIFIED.getCode()) {
            return build(o, true);
        }
        if (o.getStatus() == null || o.getStatus() != OrderStatus.PENDING_USE.getCode()) {
            throw BizException.of(ErrorCode.ORDER_NOT_PAID,
                    "订单状态是「" + OrderStatus.of(o.getStatus()).getDesc() + "」，不能核销");
        }

        Voucher v = voucherMapper.selectById(o.getVoucherId());
        LocalDateTime now = LocalDateTime.now();
        if (v != null && v.getValidEnd() != null && v.getValidEnd().isBefore(now)) {
            throw BizException.of(ErrorCode.VOUCHER_EXPIRED, "券已过有效期，建议给客人退款");
        }

        if (orderMapper.markVerified(o.getId(), now, staffUserId) == 0) {
            // 另一台收银机抢先销掉了
            Order latest = orderMapper.selectById(o.getId());
            if (latest != null && latest.getStatus() != null
                    && latest.getStatus() == OrderStatus.VERIFIED.getCode()) {
                return build(latest, true);
            }
            throw BizException.of(ErrorCode.ORDER_STATUS_ILLEGAL);
        }

        VerifyRecord r = new VerifyRecord();
        r.setOrderId(o.getId());
        r.setVerifyCode(o.getVerifyCode());
        r.setShopId(o.getShopId());
        r.setStaffId(staffUserId);
        r.setAmount(o.getPayAmount());
        try {
            verifyRecordMapper.insert(r);
        } catch (DuplicateKeyException e) {
            // uk_order 兜住。能走到这里说明订单状态更新和记录插入之间被重试过，
            // 记录已经在就是幂等的正确结果，不用报错
            log.info("核销记录已存在，按幂等处理 orderId={}", o.getId());
        }

        o.setStatus(OrderStatus.VERIFIED.getCode());
        o.setVerifyTime(now);
        o.setVerifyStaffId(staffUserId);
        return build(o, false);
    }

    /**
     * 核销员可能被限定到某家门店（tb_merchant_staff.shop_id），
     * 0 表示全部门店。不校验这个，A 店的店员能销 B 店的单。
     */
    private void requireShopAuthority(Long staffUserId, Long merchantId, Long orderShopId) {
        MerchantStaff staff = staffMapper.selectOne(Wrappers.<MerchantStaff>lambdaQuery()
                .eq(MerchantStaff::getUserId, staffUserId)
                .eq(MerchantStaff::getMerchantId, merchantId)
                .eq(MerchantStaff::getStatus, 0)
                .last("limit 1"));
        if (staff == null) {
            throw BizException.of(ErrorCode.STAFF_NOT_IN_SHOP);
        }
        Long bound = staff.getShopId();
        if (bound != null && bound != 0L && !bound.equals(orderShopId)) {
            throw BizException.of(ErrorCode.STAFF_NOT_IN_SHOP, "你只能在绑定的门店核销");
        }
    }

    private VerifyResultVO build(Order o, boolean repeated) {
        VerifyResultVO vo = new VerifyResultVO();
        vo.setOrderId(o.getId());
        vo.setOrderNo(o.getOrderNo());
        vo.setTitle(o.getTitle());
        vo.setShopId(o.getShopId());
        vo.setUserId(o.getUserId());
        vo.setQuantity(o.getQuantity());
        vo.setPayAmount(o.getPayAmount());
        vo.setVerifyTime(o.getVerifyTime());
        vo.setRepeated(repeated);

        Shop shop = shopMapper.selectById(o.getShopId());
        vo.setShopName(shop == null ? "" : shop.getName());

        User u = userMapper.selectById(o.getUserId());
        vo.setUserNickName(u == null ? "" : u.getNickName());

        Voucher v = voucherMapper.selectById(o.getVoucherId());
        vo.setValidEnd(v == null ? null : v.getValidEnd());
        return vo;
    }
}
