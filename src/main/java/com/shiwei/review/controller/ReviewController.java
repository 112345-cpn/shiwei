package com.shiwei.review.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shiwei.common.annotation.PublicApi;
import com.shiwei.common.annotation.RequireRole;
import com.shiwei.common.context.UserContext;
import com.shiwei.common.enums.RoleType;
import com.shiwei.common.result.PageResult;
import com.shiwei.common.result.Result;
import com.shiwei.merchant.service.MerchantService;
import com.shiwei.review.dto.ReviewAuditForm;
import com.shiwei.review.dto.ReviewReplyForm;
import com.shiwei.review.dto.ReviewSubmitForm;
import com.shiwei.review.entity.ReviewTag;
import com.shiwei.review.mapper.ReviewTagMapper;
import com.shiwei.review.service.ReviewService;
import com.shiwei.review.vo.ReviewSummaryVO;
import com.shiwei.review.vo.ReviewVO;
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

import java.util.List;

@Tag(name = "评价")
@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewTagMapper tagMapper;
    private final MerchantService merchantService;

    @Operation(summary = "提交评价，必须来自已核销订单")
    @PostMapping
    public Result<Long> submit(@Valid @RequestBody ReviewSubmitForm form) {
        return Result.ok(reviewService.submit(UserContext.requireUserId(), form));
    }

    @PublicApi
    @Operation(summary = "门店评价列表，sort: 0最新 1最有用 2低分优先")
    @GetMapping("/shop/{shopId}")
    public Result<PageResult<ReviewVO>> list(@PathVariable Long shopId,
                                             @RequestParam(required = false, defaultValue = "0") Integer sort,
                                             @RequestParam(defaultValue = "1") long pageNo,
                                             @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(reviewService.page(shopId, sort, pageNo, pageSize, UserContext.userId()));
    }

    @PublicApi
    @Operation(summary = "门店评价汇总：总分、分项、星级分布、高频标签")
    @GetMapping("/shop/{shopId}/summary")
    public Result<ReviewSummaryVO> summary(@PathVariable Long shopId) {
        return Result.ok(reviewService.summary(shopId));
    }

    @PublicApi
    @Operation(summary = "评价标签字典，带 typeId 时返回该分类的 + 通用标签")
    @GetMapping("/tags")
    public Result<List<ReviewTag>> tags(@RequestParam(required = false) Long typeId) {
        LambdaQueryWrapper<ReviewTag> qw = Wrappers.lambdaQuery();
        if (typeId != null && typeId > 0) {
            qw.and(w -> w.eq(ReviewTag::getTypeId, typeId).or().eq(ReviewTag::getTypeId, 0L));
        }
        qw.orderByAsc(ReviewTag::getSort);
        return Result.ok(tagMapper.selectList(qw));
    }

    @Operation(summary = "我写过的评价")
    @GetMapping("/mine")
    public Result<PageResult<ReviewVO>> mine(@RequestParam(defaultValue = "1") long pageNo,
                                             @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(reviewService.mine(UserContext.requireUserId(), pageNo, pageSize));
    }

    @Operation(summary = "标记评价有用")
    @PostMapping("/{reviewId}/useful")
    public Result<Boolean> useful(@PathVariable Long reviewId) {
        return Result.ok(reviewService.markUseful(reviewId, UserContext.requireUserId()));
    }

    @Operation(summary = "商家回复评价")
    @RequireRole(RoleType.MERCHANT)
    @PostMapping("/reply")
    public Result<Void> reply(@Valid @RequestBody ReviewReplyForm form) {
        Long merchantId = merchantService.requireMine(UserContext.requireUserId()).getId();
        reviewService.reply(merchantId, UserContext.requireUserId(), form);
        return Result.ok();
    }

    @Operation(summary = "待审核评价列表（平台运营）")
    @RequireRole(RoleType.ADMIN)
    @GetMapping("/pending")
    public Result<PageResult<ReviewVO>> pending(@RequestParam(defaultValue = "1") long pageNo,
                                                @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(reviewService.pendingAudit(pageNo, pageSize));
    }

    @Operation(summary = "审核评价（平台运营）")
    @RequireRole(RoleType.ADMIN)
    @PostMapping("/audit")
    public Result<Void> audit(@Valid @RequestBody ReviewAuditForm form) {
        reviewService.audit(form);
        return Result.ok();
    }
}
