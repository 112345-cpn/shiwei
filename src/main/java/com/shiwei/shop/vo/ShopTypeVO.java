package com.shiwei.shop.vo;

import com.shiwei.shop.entity.ShopType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ShopTypeVO {

    private Long id;
    private Long parentId;
    private String name;
    private String icon;
    private Integer sort;
    private List<ShopTypeVO> children = new ArrayList<>();

    public static ShopTypeVO of(ShopType t) {
        ShopTypeVO v = new ShopTypeVO();
        v.id = t.getId();
        v.parentId = t.getParentId();
        v.name = t.getName();
        v.icon = t.getIcon();
        v.sort = t.getSort();
        return v;
    }
}
