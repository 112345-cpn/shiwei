package com.shiwei.trade.controller;

import com.shiwei.common.annotation.RequireRole;
import com.shiwei.common.context.UserContext;
import com.shiwei.common.enums.RoleType;
import com.shiwei.common.result.Result;
import com.shiwei.trade.dto.VerifyForm;
import com.shiwei.trade.service.VerifyService;
import com.shiwei.trade.vo.VerifyResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "核销")
@RestController
@RequestMapping("/api/verify")
@RequiredArgsConstructor
public class VerifyController {

    private final VerifyService verifyService;

    @Operation(summary = "核销，重复扫码返回 repeated=true 而不是报错")
    @RequireRole(RoleType.MERCHANT)
    @PostMapping
    public Result<VerifyResultVO> verify(@Valid @RequestBody VerifyForm form) {
        return Result.ok(verifyService.verify(UserContext.requireUserId(), form));
    }
}
