package com.shiwei.shop.controller;

import com.shiwei.common.annotation.PublicApi;
import com.shiwei.common.result.PageResult;
import com.shiwei.common.result.Result;
import com.shiwei.shop.entity.ShopRating;
import com.shiwei.shop.service.ShopService;
import com.shiwei.shop.vo.ShopTypeVO;
import com.shiwei.shop.vo.ShopVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "门店")
@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;

    @PublicApi
    @Operation(summary = "分类树")
    @GetMapping("/types")
    public Result<List<ShopTypeVO>> types() {
        return Result.ok(shopService.typeTree());
    }

    @PublicApi
    @Operation(summary = "门店列表，sort: 0综合 1评分 2人气 3人均")
    @GetMapping("/page")
    public Result<PageResult<ShopVO>> page(@RequestParam(required = false) Long typeId,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false, defaultValue = "0") Integer sort,
                                           @RequestParam(defaultValue = "1") long pageNo,
                                           @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(shopService.page(typeId, keyword, sort, pageNo, pageSize));
    }

    @PublicApi
    @Operation(summary = "门店详情")
    @GetMapping("/{id}")
    public Result<ShopVO> detail(@PathVariable Long id) {
        return Result.ok(shopService.detail(id));
    }

    @PublicApi
    @Operation(summary = "门店评分明细")
    @GetMapping("/{id}/rating")
    public Result<ShopRating> rating(@PathVariable Long id) {
        return Result.ok(shopService.rating(id));
    }

    @PublicApi
    @Operation(summary = "附近门店")
    @GetMapping("/nearby")
    public Result<List<ShopVO>> nearby(@RequestParam double lat,
                                       @RequestParam double lng,
                                       @RequestParam(defaultValue = "3000") int radius,
                                       @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(shopService.nearby(lat, lng, radius, limit));
    }
}
