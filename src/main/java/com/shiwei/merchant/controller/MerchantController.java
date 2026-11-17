package com.shiwei.merchant.controller;

import com.shiwei.common.annotation.RequireRole;
import com.shiwei.common.context.UserContext;
import com.shiwei.common.enums.RoleType;
import com.shiwei.common.result.Result;
import com.shiwei.merchant.dto.MerchantApplyForm;
import com.shiwei.merchant.dto.MerchantAuditForm;
import com.shiwei.merchant.service.MerchantService;
import com.shiwei.merchant.vo.MerchantVO;
import com.shiwei.merchant.vo.StaffVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "商家入驻与管理")
@RestController
@RequestMapping("/api/merchant")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    @Operation(summary = "提交入驻申请")
    @PostMapping("/apply")
    public Result<Long> apply(@Valid @RequestBody MerchantApplyForm form) {
        return Result.ok(merchantService.apply(UserContext.requireUserId(), form));
    }

    @Operation(summary = "我的商家信息")
    @GetMapping("/my")
    public Result<MerchantVO> my() {
        return Result.ok(merchantService.my(UserContext.requireUserId()));
    }

    @Operation(summary = "当前用户可管理的商家 id")
    @GetMapping("/current")
    public Result<Map<String, Long>> current() {
        Long merchantId = merchantService.merchantIdOf(UserContext.requireUserId());
        Map<String, Long> data = new HashMap<>(1);
        data.put("merchantId", merchantId);
        return Result.ok(data);
    }

    @Operation(summary = "待审核商家列表（平台运营）")
    @RequireRole(RoleType.ADMIN)
    @GetMapping("/pending")
    public Result<List<MerchantVO>> pending() {
        return Result.ok(merchantService.pending());
    }

    @Operation(summary = "审核商家（平台运营）")
    @RequireRole(RoleType.ADMIN)
    @PostMapping("/audit")
    public Result<Void> audit(@Valid @RequestBody MerchantAuditForm form) {
        merchantService.audit(UserContext.requireUserId(), form);
        return Result.ok();
    }

    @Operation(summary = "员工列表")
    @RequireRole(RoleType.MERCHANT)
    @GetMapping("/staff")
    public Result<List<StaffVO>> staff() {
        Long merchantId = merchantService.requireMine(UserContext.requireUserId()).getId();
        return Result.ok(merchantService.staffList(merchantId));
    }

    @Operation(summary = "添加员工")
    @RequireRole(RoleType.MERCHANT)
    @PostMapping("/staff")
    public Result<Void> addStaff(@RequestParam String phone,
                                 @RequestParam(defaultValue = "1") Integer staffRole,
                                 @RequestParam(required = false) Long shopId) {
        Long merchantId = merchantService.requireMine(UserContext.requireUserId()).getId();
        merchantService.addStaff(merchantId, phone, staffRole, shopId);
        return Result.ok();
    }

    @Operation(summary = "移除员工")
    @RequireRole(RoleType.MERCHANT)
    @DeleteMapping("/staff/{staffId}")
    public Result<Void> removeStaff(@PathVariable Long staffId) {
        Long merchantId = merchantService.requireMine(UserContext.requireUserId()).getId();
        merchantService.removeStaff(merchantId, staffId);
        return Result.ok();
    }
}
