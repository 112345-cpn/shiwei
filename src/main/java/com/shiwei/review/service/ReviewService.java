package com.shiwei.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiwei.auth.entity.User;
import com.shiwei.auth.entity.UserInfo;
import com.shiwei.auth.mapper.UserInfoMapper;
import com.shiwei.auth.mapper.UserMapper;
import com.shiwei.common.config.RabbitMQConfig;
import com.shiwei.common.enums.OrderStatus;
import com.shiwei.common.enums.ReviewStatus;
import com.shiwei.common.exception.BizException;
import com.shiwei.common.exception.ErrorCode;
import com.shiwei.common.result.PageResult;
import com.shiwei.review.dto.ReviewAuditForm;
import com.shiwei.review.dto.ReviewReplyForm;
import com.shiwei.review.dto.ReviewSubmitForm;
import com.shiwei.review.entity.Review;
import com.shiwei.review.entity.ReviewReply;
import com.shiwei.review.entity.ReviewTag;
import com.shiwei.review.entity.ReviewUseful;
import com.shiwei.review.mapper.ReviewMapper;
import com.shiwei.review.mapper.ReviewReplyMapper;
import com.shiwei.review.mapper.ReviewTagMapper;
import com.shiwei.review.mapper.ReviewUsefulMapper;
import com.shiwei.review.vo.ReviewSummaryVO;
import com.shiwei.review.vo.ReviewVO;
import com.shiwei.shop.entity.Shop;
import com.shiwei.shop.entity.ShopRating;
import com.shiwei.shop.mapper.ShopMapper;
import com.shiwei.shop.mapper.ShopRatingMapper;
import com.shiwei.trade.entity.Order;
import com.shiwei.trade.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final ReviewReplyMapper replyMapper;
    private final ReviewUsefulMapper usefulMapper;
    private final ReviewTagMapper tagMapper;
    private final ShopRatingMapper shopRatingMapper;
    private final ShopMapper shopMapper;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final UserInfoMapper userInfoMapper;
    private final RabbitTemplate rabbitTemplate;

    @Value("${shiwei.review.auto-approve:false}")
    private boolean autoApprove;

    /**
     * 提交评价。三道门：
     * 1. 订单属于你
     * 2. 订单已核销 —— 没消费过不能评
     * 3. 这一单没评过 —— 唯一索引兜底并发
     */
    @Transactional(rollbackFor = Exception.class)
    public Long submit(Long userId, ReviewSubmitForm form) {
        Order order = orderMapper.selectById(form.getOrderId());
        if (order == null) {
            throw BizException.of(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw BizException.of(ErrorCode.ORDER_NOT_YOURS);
        }
        if (!Integer.valueOf(OrderStatus.VERIFIED.getCode()).equals(order.getStatus())) {
            throw BizException.of(ErrorCode.REVIEW_ORDER_REQUIRED);
        }

        Long exists = reviewMapper.selectCount(Wrappers.<Review>lambdaQuery()
                .eq(Review::getOrderId, order.getId()));
        if (exists != null && exists > 0) {
            throw BizException.of(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        Review r = new Review();
        r.setOrderId(order.getId());
        r.setShopId(order.getShopId());
        r.setUserId(userId);
        r.setScoreTotal(form.getScoreTotal());
        r.setScoreTaste(fallback(form.getScoreTaste(), form.getScoreTotal()));
        r.setScoreEnv(fallback(form.getScoreEnv(), form.getScoreTotal()));
        r.setScoreService(fallback(form.getScoreService(), form.getScoreTotal()));
        r.setContent(form.getContent() == null ? "" : form.getContent());
        r.setImages(join(form.getImages()));
        r.setTags(joinTagIds(form.getTagIds()));
        r.setUsefulCount(0);
        r.setAnonymous(Boolean.TRUE.equals(form.getAnonymous()) ? 1 : 0);
        r.setStatus(autoApprove ? ReviewStatus.APPROVED.getCode() : ReviewStatus.PENDING.getCode());
        r.setAuditReason("");

        try {
            reviewMapper.insert(r);
        } catch (DuplicateKeyException e) {
            throw BizException.of(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        userInfoMapper.update(null, Wrappers.<UserInfo>lambdaUpdate()
                .eq(UserInfo::getUserId, userId)
                .setSql("review_count = review_count + 1"));

        if (autoApprove) {
            publishRating(r.getShopId());
        }
        return r.getId();
    }

    /**
     * 门店评价列表。sort: 0 最新 1 最有用 2 评分从低到高
     */
    public PageResult<ReviewVO> page(Long shopId, Integer sort, long pageNo, long pageSize, Long currentUserId) {
        LambdaQueryWrapper<Review> qw = Wrappers.<Review>lambdaQuery()
                .eq(Review::getShopId, shopId)
                .eq(Review::getStatus, ReviewStatus.APPROVED.getCode());

        int s = sort == null ? 0 : sort;
        switch (s) {
            case 1 -> qw.orderByDesc(Review::getUsefulCount).orderByDesc(Review::getId);
            case 2 -> qw.orderByAsc(Review::getScoreTotal).orderByDesc(Review::getId);
            default -> qw.orderByDesc(Review::getId);
        }

        IPage<Review> page = reviewMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        List<ReviewVO> list = assemble(page.getRecords(), currentUserId);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), list);
    }

    public PageResult<ReviewVO> mine(Long userId, long pageNo, long pageSize) {
        IPage<Review> page = reviewMapper.selectPage(new Page<>(pageNo, pageSize),
                Wrappers.<Review>lambdaQuery()
                        .eq(Review::getUserId, userId)
                        .ne(Review::getStatus, ReviewStatus.REJECTED.getCode())
                        .orderByDesc(Review::getId));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                assemble(page.getRecords(), userId));
    }

    public ReviewSummaryVO summary(Long shopId) {
        ShopRating rating = shopRatingMapper.selectById(shopId);
        ReviewSummaryVO vo = ReviewSummaryVO.of(rating);

        // 标签统计走内存：只取最近 300 条已通过的，量大要换成预聚合表
        List<Review> recent = reviewMapper.selectList(Wrappers.<Review>lambdaQuery()
                .select(Review::getTags)
                .eq(Review::getShopId, shopId)
                .eq(Review::getStatus, ReviewStatus.APPROVED.getCode())
                .orderByDesc(Review::getId)
                .last("limit 300"));

        Map<Long, Integer> counter = new HashMap<>();
        for (Review r : recent) {
            for (Long tagId : parseTagIds(r.getTags())) {
                counter.merge(tagId, 1, Integer::sum);
            }
        }
        if (counter.isEmpty()) {
            return vo;
        }

        Map<Long, ReviewTag> tags = tagMapper.selectBatchIds(counter.keySet()).stream()
                .collect(Collectors.toMap(ReviewTag::getId, Function.identity()));

        List<ReviewSummaryVO.TagCount> top = counter.entrySet().stream()
                .filter(e -> tags.containsKey(e.getKey()))
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(6)
                .map(e -> {
                    ReviewTag t = tags.get(e.getKey());
                    ReviewSummaryVO.TagCount c = new ReviewSummaryVO.TagCount();
                    c.setId(t.getId());
                    c.setName(t.getName());
                    c.setPolarity(t.getPolarity());
                    c.setCount(e.getValue());
                    return c;
                }).toList();

        vo.setTopTags(top);
        return vo;
    }

    /**
     * 点「有用」。唯一索引当锁用：重复点会抛 DuplicateKey，直接当无事发生。
     */
    public boolean markUseful(Long reviewId, Long userId) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw BizException.of(ErrorCode.REVIEW_NOT_FOUND);
        }
        ReviewUseful u = new ReviewUseful();
        u.setReviewId(reviewId);
        u.setUserId(userId);
        try {
            usefulMapper.insert(u);
        } catch (DuplicateKeyException e) {
            return false;
        }
        reviewMapper.incrUseful(reviewId);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public void reply(Long merchantId, Long staffUserId, ReviewReplyForm form) {
        Review review = reviewMapper.selectById(form.getReviewId());
        if (review == null) {
            throw BizException.of(ErrorCode.REVIEW_NOT_FOUND);
        }
        Shop shop = shopMapper.selectById(review.getShopId());
        if (shop == null || !shop.getMerchantId().equals(merchantId)) {
            throw BizException.of(ErrorCode.FORBIDDEN, "这条评价不属于你的门店");
        }

        Long exists = replyMapper.selectCount(Wrappers.<ReviewReply>lambdaQuery()
                .eq(ReviewReply::getReviewId, form.getReviewId()));
        if (exists != null && exists > 0) {
            throw BizException.of(ErrorCode.REVIEW_ALREADY_REPLIED);
        }

        ReviewReply reply = new ReviewReply();
        reply.setReviewId(form.getReviewId());
        reply.setMerchantId(merchantId);
        reply.setStaffId(staffUserId);
        reply.setContent(form.getContent());
        replyMapper.insert(reply);
    }

    @Transactional(rollbackFor = Exception.class)
    public void audit(ReviewAuditForm form) {
        Review review = reviewMapper.selectById(form.getReviewId());
        if (review == null) {
            throw BizException.of(ErrorCode.REVIEW_NOT_FOUND);
        }

        boolean pass = Boolean.TRUE.equals(form.getPass());
        review.setStatus(pass ? ReviewStatus.APPROVED.getCode() : ReviewStatus.REJECTED.getCode());
        review.setAuditReason(form.getReason() == null ? "" : form.getReason());
        reviewMapper.updateById(review);

        // 通过和被拒都要重算：从待审变通过要加进来，从通过变隐藏要减出去
        publishRating(review.getShopId());
    }

    public PageResult<ReviewVO> pendingAudit(long pageNo, long pageSize) {
        IPage<Review> page = reviewMapper.selectPage(new Page<>(pageNo, pageSize),
                Wrappers.<Review>lambdaQuery()
                        .eq(Review::getStatus, ReviewStatus.PENDING.getCode())
                        .orderByAsc(Review::getId));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                assemble(page.getRecords(), null));
    }

    /**
     * 投递评分重算消息。
     * 这里必须吞掉异常：评价已经落库了，MQ 挂了不该让用户提交失败。
     * 漏算的那一次由凌晨的校准任务补回来。
     */
    private void publishRating(Long shopId) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_SHOP, RabbitMQConfig.RK_RATING, shopId);
        } catch (Exception e) {
            log.warn("评分消息投递失败，等定时校准兜底 shopId={}", shopId, e);
        }
    }

    /**
     * 把原始行补齐作者、回复、标签、当前用户是否点过有用。
     * 四个批量查询，不做 N+1。
     */
    private List<ReviewVO> assemble(List<Review> reviews, Long currentUserId) {
        if (reviews.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> reviewIds = reviews.stream().map(Review::getId).toList();
        List<Long> userIds = reviews.stream().map(Review::getUserId).distinct().toList();

        Map<Long, User> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Map<Long, ReviewReply> replies = replyMapper.selectList(Wrappers.<ReviewReply>lambdaQuery()
                        .in(ReviewReply::getReviewId, reviewIds)).stream()
                .collect(Collectors.toMap(ReviewReply::getReviewId, Function.identity(), (a, b) -> a));

        Set<Long> myUseful = currentUserId == null ? Collections.emptySet()
                : usefulMapper.selectList(Wrappers.<ReviewUseful>lambdaQuery()
                        .eq(ReviewUseful::getUserId, currentUserId)
                        .in(ReviewUseful::getReviewId, reviewIds)).stream()
                .map(ReviewUseful::getReviewId)
                .collect(Collectors.toSet());

        List<Long> allTagIds = reviews.stream()
                .flatMap(r -> parseTagIds(r.getTags()).stream())
                .distinct().toList();
        Map<Long, ReviewTag> tags = allTagIds.isEmpty() ? Collections.emptyMap()
                : tagMapper.selectBatchIds(allTagIds).stream()
                .collect(Collectors.toMap(ReviewTag::getId, Function.identity()));

        return reviews.stream().map(r -> {
            ReviewVO v = new ReviewVO();
            v.setId(r.getId());
            v.setShopId(r.getShopId());
            v.setUserId(r.getUserId());

            User author = users.get(r.getUserId());
            boolean anonymous = r.getAnonymous() != null && r.getAnonymous() == 1;
            v.setUserNickName(anonymous ? "匿名用户" : (author == null ? "已注销用户" : author.getNickName()));
            v.setUserIcon(anonymous ? "" : (author == null ? "" : author.getIcon()));

            v.setScoreTotal(r.getScoreTotal());
            v.setScoreTaste(r.getScoreTaste());
            v.setScoreEnv(r.getScoreEnv());
            v.setScoreService(r.getScoreService());
            v.setContent(r.getContent());
            v.setCreateTime(r.getCreateTime());
            v.setUsefulCount(r.getUsefulCount());
            v.setUseful(myUseful.contains(r.getId()));

            if (r.getImages() != null && !r.getImages().isBlank()) {
                v.setImages(new ArrayList<>(Arrays.asList(r.getImages().split(","))));
            }

            for (Long tagId : parseTagIds(r.getTags())) {
                ReviewTag t = tags.get(tagId);
                if (t != null) {
                    ReviewVO.Tag tag = new ReviewVO.Tag();
                    tag.setId(t.getId());
                    tag.setName(t.getName());
                    tag.setPolarity(t.getPolarity());
                    v.getTags().add(tag);
                }
            }

            ReviewReply reply = replies.get(r.getId());
            if (reply != null) {
                ReviewVO.Reply rp = new ReviewVO.Reply();
                rp.setContent(reply.getContent());
                rp.setCreateTime(reply.getCreateTime());
                v.setReply(rp);
            }
            return v;
        }).toList();
    }

    private Integer fallback(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String join(List<String> images) {
        if (images == null || images.isEmpty()) {
            return "";
        }
        return String.join(",", images);
    }

    private String joinTagIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return "";
        }
        return tagIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<Long> parseTagIds(String tags) {
        if (tags == null || tags.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(tags.split(","))
                .filter(s -> !s.isBlank())
                .map(Long::valueOf)
                .toList();
    }
}
