package com.shiwei.common.enums;

import lombok.Getter;

@Getter
public enum RoleType {

    CONSUMER(0, "消费者"),
    MERCHANT(1, "商家"),
    ADMIN(2, "平台运营");

    private final int code;
    private final String desc;

    RoleType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static RoleType of(Integer code) {
        if (code == null) {
            return CONSUMER;
        }
        for (RoleType v : values()) {
            if (v.code == code) {
                return v;
            }
        }
        return CONSUMER;
    }
}
