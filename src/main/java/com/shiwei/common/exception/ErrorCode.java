package com.shiwei.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    OK(0, "ok"),

    BAD_REQUEST(40000, "请求参数有误"),
    UNAUTHORIZED(40100, "请先登录"),
    FORBIDDEN(40300, "没有操作权限"),
    NOT_FOUND(40400, "资源不存在"),
    METHOD_NOT_ALLOWED(40500, "请求方式不支持"),
    TOO_MANY_REQUESTS(42900, "操作太频繁，稍后再试"),
    INTERNAL_ERROR(50000, "服务开小差了"),

    AUTH_CODE_INVALID(40101, "验证码不正确或已过期"),
    AUTH_CODE_TOO_FREQUENT(40102, "验证码发送太频繁"),
    AUTH_ACCOUNT_BANNED(40103, "账号已被封禁"),
    AUTH_BAD_CREDENTIAL(40104, "手机号或密码不正确"),

    USER_NOT_FOUND(40401, "用户不存在"),
    USER_PHONE_EXISTS(40901, "手机号已注册"),

    MERCHANT_NOT_FOUND(40402, "商家不存在"),
    MERCHANT_AUDIT_PENDING(40301, "商家资质正在审核中"),
    MERCHANT_AUDIT_REJECTED(40302, "商家资质未通过审核"),
    MERCHANT_LICENSE_EXISTS(40902, "该营业执照已入驻"),
    MERCHANT_NOT_OWNER(40303, "不是该商家的负责人"),
    STAFF_NOT_IN_SHOP(40304, "无权在该门店核销"),

    SHOP_NOT_FOUND(40403, "门店不存在"),
    SHOP_OFFLINE(40305, "门店已下线"),

    REVIEW_ORDER_REQUIRED(40306, "只有消费过的订单才能评价"),
    REVIEW_ALREADY_EXISTS(40903, "该订单已评价"),
    REVIEW_NOT_FOUND(40404, "评价不存在"),
    REVIEW_NOT_YOURS(40307, "不能操作他人的评价"),
    REVIEW_ALREADY_REPLIED(40904, "这条评价已回复过"),

    VOUCHER_NOT_FOUND(40405, "券不存在"),
    VOUCHER_OFF_SHELF(40308, "券已下架"),
    VOUCHER_NOT_STARTED(40309, "还没到开售时间"),
    VOUCHER_EXPIRED(40310, "券已过期"),
    VOUCHER_SOLD_OUT(40905, "已被抢完"),
    VOUCHER_STOCK_SHORT(40906, "库存不足"),
    VOUCHER_LIMIT_EXCEEDED(40907, "超出限购数量"),

    ORDER_NOT_FOUND(40406, "订单不存在"),
    ORDER_NOT_YOURS(40311, "不是你的订单"),
    ORDER_STATUS_ILLEGAL(40908, "订单当前状态不允许该操作"),
    ORDER_ALREADY_PAID(40909, "订单已支付"),
    ORDER_EXPIRED(40910, "订单已超时关闭"),
    ORDER_ALREADY_VERIFIED(40911, "订单已核销"),
    ORDER_NOT_PAID(40912, "订单未支付"),
    ORDER_REFUND_WINDOW_CLOSED(40913, "已超过可退期限"),

    VERIFY_CODE_INVALID(40312, "核销码不正确"),

    PAY_FAILED(50201, "支付失败"),
    REFUND_FAILED(50202, "退款失败"),
    BALANCE_NOT_ENOUGH(40914, "余额不足");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
