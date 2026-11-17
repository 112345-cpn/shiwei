package com.shiwei.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiwei.common.enums.VoucherStatus;
import com.shiwei.common.exception.BizException;
import com.shiwei.common.exception.ErrorCode;
import com.shiwei.common.result.PageResult;
import com.shiwei.shop.entity.Shop;
import com.shiwei.shop.mapper.ShopMapper;
import com.shiwei.trade.dto.VoucherForm;
import com.shiwei.trade.entity.Voucher;
import com.shiwei.trade.mapper.VoucherMapper;
import com.shiwei.trade.vo.VoucherVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 券的供给侧。券由商家建、商家改、商家上下架，平台只在审核环节介入（审核先不做，
 * 靠商家资质审核把关已经够了，多一层反而拖慢上架）。
 */
@Service
@RequiredArgsConstructor
public class VoucherService {

    private final VoucherMapper voucherMapper;
    private final ShopMapper shopMapper;

    @Transactional(rollbackFor = Exception.class)
    public Long create(Long merchantId, VoucherForm form) {
        Shop shop = requireShopOf(merchantId, form.getShopId());
        validateTimeWindow(form);

        Voucher v = new Voucher();
        v.setMerchantId(merchantId);
        v.setShopId(shop.getId());
        applyForm(v, form);
        // 新建先落草稿，商家确认无误再上架。直接上架容易出现建完就有错价在售
        v.setStatus(VoucherStatus.DRAFT.getCode());
        v.setSold(0);
        voucherMapper.insert(v);
        return v.getId();
    }

    /**
     * 改券。stock 是「剩余库存」不是「总库存」，sold 是累计卖出，
     * 两者不要求 stock >= sold —— 商家补货前 stock 小于 sold 是正常的。
     * 唯一要守住的是改完之后库存不能为负，那由表单的 @Min(0) 保证。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long merchantId, Long voucherId, VoucherForm form) {
        Voucher v = requireOwn(merchantId, voucherId);
        if (v.getStatus() != null && v.getStatus() == VoucherStatus.ON_SHELF.getCode()) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "上架中的券不能改，先下架");
        }
        validateTimeWindow(form);

        applyForm(v, form);
        voucherMapper.updateById(v);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long merchantId, Long voucherId, Integer status) {
        Voucher v = requireOwn(merchantId, voucherId);
        int target = status == null ? VoucherStatus.OFF_SHELF.getCode() : status;
        if (target == VoucherStatus.ON_SHELF.getCode() && (v.getStock() == null || v.getStock() <= 0)) {
            throw BizException.of(ErrorCode.VOUCHER_STOCK_SHORT, "库存为 0，不能上架");
        }
        v.setStatus(target);
        voucherMapper.updateById(v);
    }

    /** 商家后台：自己所有门店的券，status 为空则全部。 */
    public PageResult<VoucherVO> merchantPage(Long merchantId, Integer status, long pageNo, long pageSize) {
        LambdaQueryWrapper<Voucher> qw = Wrappers.<Voucher>lambdaQuery()
                .eq(Voucher::getMerchantId, merchantId)
                .eq(status != null, Voucher::getStatus, status)
                .orderByDesc(Voucher::getId);
        IPage<Voucher> page = voucherMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), assemble(page.getRecords()));
    }

    /**
     * 门店详情页的在售券。这里只按「上架 + 没停售」过滤，
     * 库存为 0 的仍然返回，前端显示「已抢完」—— 直接过滤掉会让用户以为这家店没有优惠。
     */
    public List<VoucherVO> onSaleOfShop(Long shopId) {
        LocalDateTime now = LocalDateTime.now();
        List<Voucher> list = voucherMapper.selectList(Wrappers.<Voucher>lambdaQuery()
                .eq(Voucher::getShopId, shopId)
                .eq(Voucher::getStatus, VoucherStatus.ON_SHELF.getCode())
                .le(Voucher::getSaleBegin, now)
                .ge(Voucher::getSaleEnd, now)
                .orderByAsc(Voucher::getSalePrice));
        List<VoucherVO> vos = assemble(list);
        vos.forEach(v -> v.setOnSale(v.getStock() != null && v.getStock() > 0));
        return vos;
    }

    public VoucherVO detail(Long voucherId) {
        Voucher v = voucherMapper.selectById(voucherId);
        if (v == null) {
            throw BizException.of(ErrorCode.VOUCHER_NOT_FOUND);
        }
        VoucherVO vo = assemble(List.of(v)).get(0);
        vo.setOnSale(v.getStatus() != null
                && v.getStatus() == VoucherStatus.ON_SHELF.getCode()
                && v.getStock() != null && v.getStock() > 0
                && v.getSaleBegin() != null && v.getSaleBegin().isBefore(LocalDateTime.now())
                && v.getSaleEnd() != null && v.getSaleEnd().isAfter(LocalDateTime.now()));
        return vo;
    }

    private Voucher requireOwn(Long merchantId, Long voucherId) {
        Voucher v = voucherMapper.selectById(voucherId);
        if (v == null) {
            throw BizException.of(ErrorCode.VOUCHER_NOT_FOUND);
        }
        if (!v.getMerchantId().equals(merchantId)) {
            throw BizException.of(ErrorCode.FORBIDDEN, "这张券不属于你的门店");
        }
        return v;
    }

    private Shop requireShopOf(Long merchantId, Long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw BizException.of(ErrorCode.SHOP_NOT_FOUND);
        }
        if (!shop.getMerchantId().equals(merchantId)) {
            throw BizException.of(ErrorCode.FORBIDDEN, "这家门店不属于你");
        }
        return shop;
    }

    private void applyForm(Voucher v, VoucherForm f) {
        v.setShopId(f.getShopId());
        v.setTitle(f.getTitle());
        v.setSubTitle(f.getSubTitle() == null ? "" : f.getSubTitle());
        v.setRules(f.getRules() == null ? "" : f.getRules());
        v.setType(f.getType());
        v.setOriginalPrice(f.getOriginalPrice());
        v.setSalePrice(f.getSalePrice());
        v.setStock(f.getStock());
        v.setSaleBegin(f.getSaleBegin());
        v.setSaleEnd(f.getSaleEnd());
        v.setValidBegin(f.getValidBegin());
        v.setValidEnd(f.getValidEnd());
    }

    /**
     * 时间窗口校验。顺序错了会让用户买到「券本身还没生效」或「用的时候已经过期」的东西，
     * 这四组关系必须成立：
     * 开售 < 停售、有效期开始 < 有效期结束、售价 <= 原价、有效期结束 > 停售（卖出去的时候还能用）。
     */
    private void validateTimeWindow(VoucherForm f) {
        if (!f.getSaleBegin().isBefore(f.getSaleEnd())) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "停售时间必须晚于开售时间");
        }
        if (!f.getValidBegin().isBefore(f.getValidEnd())) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "有效期结束必须晚于开始");
        }
        if (f.getValidEnd().isBefore(f.getSaleEnd())) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "有效期结束不能早于停售时间");
        }
        if (f.getSalePrice() > f.getOriginalPrice()) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "售价不能高于原价");
        }
    }

    /** 补齐门店名。一页券通常只涉及少数门店，一次批量查就够，不做 N+1。 */
    private List<VoucherVO> assemble(List<Voucher> list) {
        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> shopIds = list.stream().map(Voucher::getShopId).distinct().toList();
        Map<Long, Shop> shops = shopMapper.selectBatchIds(shopIds).stream()
                .collect(Collectors.toMap(Shop::getId, Function.identity(), (a, b) -> a));

        return list.stream().map(v -> {
            VoucherVO vo = VoucherVO.of(v);
            Shop s = shops.get(v.getShopId());
            vo.setShopName(s == null ? "" : s.getName());
            return vo;
        }).collect(Collectors.toCollection(ArrayList::new));
    }

    /** 给别的 Service 用的轻量校验：券能不能卖。 */
    public Voucher requireOnSale(Long voucherId) {
        Voucher v = voucherMapper.selectById(voucherId);
        if (v == null) {
            throw BizException.of(ErrorCode.VOUCHER_NOT_FOUND);
        }
        if (v.getStatus() == null || v.getStatus() != VoucherStatus.ON_SHELF.getCode()) {
            throw BizException.of(ErrorCode.VOUCHER_OFF_SHELF);
        }
        LocalDateTime now = LocalDateTime.now();
        if (v.getSaleBegin() != null && now.isBefore(v.getSaleBegin())) {
            throw BizException.of(ErrorCode.VOUCHER_NOT_STARTED);
        }
        if (v.getSaleEnd() != null && now.isAfter(v.getSaleEnd())) {
            throw BizException.of(ErrorCode.VOUCHER_EXPIRED);
        }
        return v;
    }
}
