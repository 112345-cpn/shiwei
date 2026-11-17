package com.shiwei.trade.controller;

import com.shiwei.common.annotation.RequireRole;
import com.shiwei.common.context.UserContext;
import com.shiwei.common.enums.RoleType;
import com.shiwei.common.exception.BizException;
import com.shiwei.common.exception.ErrorCode;
import com.shiwei.common.result.PageResult;
import com.shiwei.common.result.Result;
import com.shiwei.merchant.service.MerchantService;
import com.shiwei.trade.dto.RefundApplyForm;
import com.shiwei.trade.dto.RefundAuditForm;
import com.shiwei.trade.service.RefundService;
import com.shiwei.trade.vo.RefundVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "退款")
@RestController
@RequestMapping("/api/refund")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;
    private final MerchantService merchantService;

    @Operation(summary = "申请退款，已核销的订单不能退")
    @PostMapping
    public Result<Long> apply(@Valid @RequestBody RefundApplyForm form) {
        return Result.ok(refundService.apply(UserContext.requireUserId(), form));
    }

    @Operation(summary = "商家审核退款")
    @RequireRole(RoleType.MERCHANT)
    @PostMapping("/audit")
    public Result<Void> audit(@Valid @RequestBody RefundAuditForm form) {
        refundService.audit(requireMerchantId(), UserContext.requireUserId(), form);
        return Result.ok();
    }

    @Operation(summary = "我的退款记录")
    @GetMapping("/mine")
    public Result<PageResult<RefundVO>> mine(@RequestParam(defaultValue = "1") long pageNo,
                                            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(refundService.myRefunds(UserContext.requireUserId(), pageNo, pageSize));
    }

    @Operation(summary = "商家侧退款列表")
    @RequireRole(RoleType.MERCHANT)
    @GetMapping("/merchant/page")
    public Result<PageResult<RefundVO>> merchantPage(@RequestParam(required = false) Integer status,
                                                     @RequestParam(defaultValue = "1") long pageNo,
                                                     @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(refundService.merchantRefunds(requireMerchantId(), status, pageNo, pageSize));
    }

    @Operation(summary = "待处理退款数，给商家后台红点用")
    @RequireRole(RoleType.MERCHANT)
    @GetMapping("/merchant/pending-count")
    public Result<Long> pendingCount() {
        return Result.ok(refundService.pendingCount(requireMerchantId()));
    }

    private Long requireMerchantId() {
        Long merchantId = merchantService.merchantIdOf(UserContext.requireUserId());
        if (merchantId == null) {
            throw BizException.of(ErrorCode.MERCHANT_NOT_OWNER);
        }
        return merchantId;
    }
}
