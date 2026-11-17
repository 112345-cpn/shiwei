package com.shiwei.common.enums;

import lombok.Getter;

@Getter
public enum OrderStatus {

    PENDING_PAY(0, "待支付"),
    PENDING_USE(1, "待使用"),
    VERIFIED(2, "已核销"),
    REFUNDING(3, "退款中"),
    REFUNDED(4, "已退款"),
    CLOSED(5, "已关闭");

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static OrderStatus of(Integer code) {
        for (OrderStatus v : values()) {
            if (v.code == code) {
                return v;
            }
        }
        return CLOSED;
    }

    public boolean canPay() {
        return this == PENDING_PAY;
    }

    public boolean canVerify() {
        return this == PENDING_USE;
    }

    public boolean canRefund() {
        return this == PENDING_USE;
    }

    public boolean canClose() {
        return this == PENDING_PAY;
    }
}
