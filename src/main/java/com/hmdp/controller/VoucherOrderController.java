package com.hmdp.controller;

import com.hmdp.annotation.RateLimiter;
import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * 秒杀订单 Controller
 *
 * 限流策略：
 * - @RateLimiter(name="seckill", qps=100) 全局限流 100 QPS
 * - AntiBrushInterceptor 单用户 5 QPS
 * - 二者组合形成双重防护
 *
 * @author HM-DianPing
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    public IVoucherOrderService voucherOrderService;

    /**
     * 秒杀入口（双重限流）
     */
    @RateLimiter(name = "seckill", qps = 100, message = "秒杀太火爆，已被限流，请稍后再试")
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }
}