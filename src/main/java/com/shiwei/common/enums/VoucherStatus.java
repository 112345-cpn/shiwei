package com.shiwei.common.enums;

import lombok.Getter;

@Getter
public enum VoucherStatus {

    DRAFT(0, "草稿"),
    ON_SHELF(1, "上架"),
    OFF_SHELF(2, "下架");

    private final int code;
    private final String desc;

    VoucherStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
