package com.shiwei.common.enums;

import lombok.Getter;

@Getter
public enum ReviewStatus {

    PENDING(0, "待审核"),
    APPROVED(1, "已通过"),
    REJECTED(2, "已拒绝"),
    HIDDEN(3, "已隐藏");

    private final int code;
    private final String desc;

    ReviewStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
