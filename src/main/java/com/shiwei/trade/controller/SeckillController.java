package com.shiwei.trade.controller;

import com.shiwei.common.annotation.RequireRole;
import com.shiwei.common.context.UserContext;
import com.shiwei.common.enums.RoleType;
import com.shiwei.common.result.Result;
import com.shiwei.trade.service.SeckillService;
import com.shiwei.trade.vo.SeckillResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "秒杀")
@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    @Operation(summary = "抢购，返回 PENDING 表示排队中，要再查结果")
    @PostMapping("/{voucherId}")
    public Result<SeckillResultVO> seckill(@PathVariable Long voucherId) {
        return Result.ok(seckillService.seckill(UserContext.requireUserId(), voucherId));
    }

    @Operation(summary = "查抢购结果，前端轮询这个")
    @GetMapping("/{voucherId}/result")
    public Result<SeckillResultVO> result(@PathVariable Long voucherId) {
        return Result.ok(seckillService.result(UserContext.requireUserId(), voucherId));
    }

    @Operation(summary = "手动预热（运营）")
    @RequireRole(RoleType.ADMIN)
    @PostMapping("/warm-up")
    public Result<Integer> warmUp() {
        return Result.ok(seckillService.warmUp());
    }
}
