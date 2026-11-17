package com.shiwei.trade.controller;

import com.shiwei.common.annotation.PublicApi;
import com.shiwei.common.annotation.RequireRole;
import com.shiwei.common.context.UserContext;
import com.shiwei.common.enums.RoleType;
import com.shiwei.common.exception.BizException;
import com.shiwei.common.exception.ErrorCode;
import com.shiwei.common.result.PageResult;
import com.shiwei.common.result.Result;
import com.shiwei.merchant.service.MerchantService;
import com.shiwei.trade.dto.VoucherForm;
import com.shiwei.trade.service.VoucherService;
import com.shiwei.trade.vo.VoucherVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "券")
@RestController
@RequestMapping("/api/voucher")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;
    private final MerchantService merchantService;

    @Operation(summary = "商家建券（先落草稿）")
    @RequireRole(RoleType.MERCHANT)
    @PostMapping
    public Result<Long> create(@Valid @RequestBody VoucherForm form) {
        return Result.ok(voucherService.create(requireMerchantId(), form));
    }

    @Operation(summary = "商家改券，上架中的券要先下架")
    @RequireRole(RoleType.MERCHANT)
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody VoucherForm form) {
        voucherService.update(requireMerchantId(), id, form);
        return Result.ok();
    }

    @Operation(summary = "上下架，status: 1 上架 2 下架")
    @RequireRole(RoleType.MERCHANT)
    @PostMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        voucherService.changeStatus(requireMerchantId(), id, status);
        return Result.ok();
    }

    @Operation(summary = "商家自己的券列表")
    @RequireRole(RoleType.MERCHANT)
    @GetMapping("/merchant/page")
    public Result<PageResult<VoucherVO>> merchantPage(@RequestParam(required = false) Integer status,
                                                      @RequestParam(defaultValue = "1") long pageNo,
                                                      @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(voucherService.merchantPage(requireMerchantId(), status, pageNo, pageSize));
    }

    @PublicApi
    @Operation(summary = "门店在售券，库存为 0 也返回，前端显示已抢完")
    @GetMapping("/shop/{shopId}")
    public Result<List<VoucherVO>> ofShop(@PathVariable Long shopId) {
        return Result.ok(voucherService.onSaleOfShop(shopId));
    }

    @PublicApi
    @Operation(summary = "券详情")
    @GetMapping("/{id}")
    public Result<VoucherVO> detail(@PathVariable Long id) {
        return Result.ok(voucherService.detail(id));
    }

    private Long requireMerchantId() {
        Long merchantId = merchantService.merchantIdOf(UserContext.requireUserId());
        if (merchantId == null) {
            throw BizException.of(ErrorCode.MERCHANT_NOT_OWNER);
        }
        return merchantId;
    }
}
