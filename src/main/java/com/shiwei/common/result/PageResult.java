package com.shiwei.common.result;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {

    private long total;
    private long pageNo;
    private long pageSize;
    private List<T> list;

    public static <T> PageResult<T> of(long total, long pageNo, long pageSize, List<T> list) {
        PageResult<T> p = new PageResult<>();
        p.total = total;
        p.pageNo = pageNo;
        p.pageSize = pageSize;
        p.list = list;
        return p;
    }
}
