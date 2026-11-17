package com.shiwei.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiwei.common.result.PageResult;
import com.shiwei.shop.entity.Shop;
import com.shiwei.shop.entity.ShopHour;
import com.shiwei.shop.entity.ShopRating;
import com.shiwei.shop.entity.ShopType;
import com.shiwei.shop.mapper.ShopHourMapper;
import com.shiwei.shop.mapper.ShopMapper;
import com.shiwei.shop.mapper.ShopRatingMapper;
import com.shiwei.shop.mapper.ShopTypeMapper;
import com.shiwei.shop.vo.ShopTypeVO;
import com.shiwei.shop.vo.ShopVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShopService {

    private static final double EARTH_RADIUS = 6371000D;

    private final ShopMapper shopMapper;
    private final ShopTypeMapper shopTypeMapper;
    private final ShopHourMapper shopHourMapper;
    private final ShopRatingMapper shopRatingMapper;

    public List<ShopTypeVO> typeTree() {
        List<ShopType> all = shopTypeMapper.selectList(Wrappers.<ShopType>lambdaQuery()
                .orderByAsc(ShopType::getSort));
        Map<Long, ShopTypeVO> index = new HashMap<>();
        List<ShopTypeVO> roots = new ArrayList<>();

        for (ShopType t : all) {
            if (t.getParentId() == null || t.getParentId() == 0) {
                ShopTypeVO vo = ShopTypeVO.of(t);
                index.put(t.getId(), vo);
                roots.add(vo);
            }
        }
        for (ShopType t : all) {
            if (t.getParentId() != null && t.getParentId() != 0) {
                ShopTypeVO parent = index.get(t.getParentId());
                if (parent != null) {
                    parent.getChildren().add(ShopTypeVO.of(t));
                }
            }
        }
        return roots;
    }

    /**
     * 门店列表。sort: 0 综合 1 评分 2 人气 3 人均从低到高
     */
    public PageResult<ShopVO> page(Long typeId, String keyword, Integer sort, long pageNo, long pageSize) {
        LambdaQueryWrapper<Shop> qw = Wrappers.<Shop>lambdaQuery()
                .eq(Shop::getStatus, 0);

        if (typeId != null && typeId > 0) {
            List<Long> ids = subTypeIds(typeId);
            qw.in(Shop::getTypeId, ids);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(Shop::getName, keyword).or().like(Shop::getArea, keyword));
        }

        int s = sort == null ? 0 : sort;
        switch (s) {
            case 1 -> qw.orderByDesc(Shop::getRating).orderByDesc(Shop::getRatingCount);
            case 2 -> qw.orderByDesc(Shop::getSold);
            case 3 -> qw.orderByAsc(Shop::getAvgPrice);
            default -> qw.orderByDesc(Shop::getRatingCount).orderByDesc(Shop::getRating);
        }

        IPage<Shop> page = shopMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        List<ShopVO> list = page.getRecords().stream().map(ShopVO::of).toList();
        fillTypeName(list);

        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), list);
    }

    public ShopVO detail(Long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            return null;
        }
        ShopVO vo = ShopVO.of(shop);

        ShopType type = shopTypeMapper.selectById(shop.getTypeId());
        vo.setTypeName(type == null ? "" : type.getName());

        List<ShopHour> hours = shopHourMapper.selectList(Wrappers.<ShopHour>lambdaQuery()
                .eq(ShopHour::getShopId, shopId)
                .orderByAsc(ShopHour::getDayOfWeek)
                .orderByAsc(ShopHour::getOpenTime));
        vo.setHours(hours.stream().map(h -> {
            ShopVO.OpenHour o = new ShopVO.OpenHour();
            o.setDayOfWeek(h.getDayOfWeek());
            o.setOpenTime(h.getOpenTime() == null ? "" : h.getOpenTime().toString());
            o.setCloseTime(h.getCloseTime() == null ? "" : h.getCloseTime().toString());
            return o;
        }).toList());

        return vo;
    }

    public ShopRating rating(Long shopId) {
        return shopRatingMapper.selectById(shopId);
    }

    public List<ShopVO> nearby(double lat, double lng, int radiusMeters, int limit) {
        double dLat = Math.toDegrees(radiusMeters / EARTH_RADIUS);
        double dLng = Math.toDegrees(radiusMeters / (EARTH_RADIUS * Math.cos(Math.toRadians(lat))));

        int cap = Math.min(Math.max(limit, 1), 50);
        List<ShopVO> list = shopMapper.selectNearby(lat, lng, dLat, dLng, cap)
                .stream().map(ShopVO::of).toList();
        fillTypeName(list);
        return list;
    }

    private List<Long> subTypeIds(Long typeId) {
        List<ShopType> children = shopTypeMapper.selectList(Wrappers.<ShopType>lambdaQuery()
                .eq(ShopType::getParentId, typeId));
        if (children.isEmpty()) {
            return List.of(typeId);
        }
        List<Long> ids = children.stream().map(ShopType::getId).collect(Collectors.toList());
        ids.add(typeId);
        return ids;
    }

    private void fillTypeName(List<ShopVO> list) {
        if (list.isEmpty()) {
            return;
        }
        List<Long> typeIds = list.stream().map(ShopVO::getTypeId).distinct().toList();
        Map<Long, String> names = shopTypeMapper.selectBatchIds(typeIds).stream()
                .collect(Collectors.toMap(ShopType::getId, ShopType::getName));
        list.forEach(v -> v.setTypeName(names.getOrDefault(v.getTypeId(), "")));
    }
}
