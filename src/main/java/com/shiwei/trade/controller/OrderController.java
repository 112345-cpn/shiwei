package com.shiwei.trade.controller;

import com.shiwei.common.annotation.RequireRole;
import com.shiwei.common.context.UserContext;
import com.shiwei.common.enums.RoleType;
import com.shiwei.common.exception.BizException;
import com.shiwei.common.exception.ErrorCode;
import com.shiwei.common.result.PageResult;
import com.shiwei.common.result.Result;
import com.shiwei.merchant.service.MerchantService;
import com.shiwei.trade.dto.OrderSubmitForm;
import com.shiwei.trade.dto.PayForm;
import com.shiwei.trade.service.OrderService;
import com.shiwei.trade.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "订单")
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final MerchantService merchantService;

    @Operation(summary = "下单，库存扣减在这里完成")
    @PostMapping
    public Result<Long> submit(@Valid @RequestBody OrderSubmitForm form) {
        return Result.ok(orderService.submit(UserContext.requireUserId(), form));
    }

    @Operation(summary = "支付")
    @PostMapping("/{id}/pay")
    public Result<Void> pay(@PathVariable Long id, @RequestBody(required = false) PayForm form) {
        orderService.pay(UserContext.requireUserId(), id, form);
        return Result.ok();
    }

    @Operation(summary = "我的订单，status 为空查全部")
    @GetMapping("/mine")
    public Result<PageResult<OrderVO>> mine(@RequestParam(required = false) Integer status,
                                            @RequestParam(defaultValue = "1") long pageNo,
                                            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(orderService.myOrders(UserContext.requireUserId(), status, pageNo, pageSize));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{id}")
    public Result<OrderVO> detail(@PathVariable Long id) {
        return Result.ok(orderService.detail(UserContext.requireUserId(), id));
    }

    @Operation(summary = "商家侧订单列表")
    @RequireRole(RoleType.MERCHANT)
    @GetMapping("/merchant/page")
    public Result<PageResult<OrderVO>> merchantPage(@RequestParam(required = false) Integer status,
                                                    @RequestParam(defaultValue = "1") long pageNo,
                                                    @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(orderService.merchantOrders(requireMerchantId(), status, pageNo, pageSize));
    }

    private Long requireMerchantId() {
        Long merchantId = merchantService.merchantIdOf(UserContext.requireUserId());
        if (merchantId == null) {
            throw BizException.of(ErrorCode.MERCHANT_NOT_OWNER);
        }
        return merchantId;
    }
}
