package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 商户服务实现（缓存三连完整解决方案版）
 *
 * 缓存三大问题对应实现：
 * 1. queryById 使用 queryWithLogicalExpire（逻辑过期）—— 适合读多写少
 * 2. 缓存更新：先更新 DB，再删除缓存（Cache-Aside 模式）
 *
 * @author HM-DianPing
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private final StringRedisTemplate stringRedisTemplate;
    private final CacheClient cacheClient;

    /** 布隆过滤器名称（用于缓存穿透防护） */
    private static final String SHOP_BLOOM_FILTER = "bloom:filter:shop";

    // ============================================
    // 缓存更新（Cache-Aside 旁路缓存模式）
    // ============================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }

        // 1. 先更新 DB
        boolean updated = this.updateById(shop);
        if (!updated) {
            return Result.fail("更新失败");
        }

        // 2. 再删除缓存（保证数据一致性）
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        log.info("店铺更新成功，缓存已删除：id={}", id);
        return Result.ok();
    }

    // ============================================
    // 缓存查询（逻辑过期方案，防击穿）
    // ============================================

    @Override
    public Result queryById(Long id) {
        // 使用逻辑过期方案：适合读多写少，热点数据永不过期阻塞
        Shop shop = cacheClient.queryWithLogicalExpire(
                RedisConstants.CACHE_SHOP_KEY,
                id,
                Shop.class,
                this::getById,  // DB 查询函数
                RedisConstants.CACHE_LOGIC_EXPIRE_TTL
        );

        if (shop == null) {
            return Result.fail("店铺不存在或缓存未预热");
        }
        return Result.ok(shop);
    }

    /**
     * 缓存预热（项目启动时调用，把热点数据写入 Redis）
     */
    @Override
    public void warmUpCache() {
        log.info("开始预热商户缓存...");
        Executors.newSingleThreadExecutor().submit(() -> {
            // 预热前 100 个商户（实际项目按热点度筛选）
            List<Shop> shops = this.query().last("LIMIT 100").list();
            for (Shop shop : shops) {
                cacheClient.setWithLogicalExpire(
                        RedisConstants.CACHE_SHOP_KEY + shop.getId(),
                        shop,
                        RedisConstants.CACHE_LOGIC_EXPIRE_TTL
                );
            }
            log.info("商户缓存预热完成：共 {} 条", shops.size());
        });
    }

    // ============================================
    // 附近商户（GEO 地理位置查询）
    // ============================================

    @Override
    public Result queryShopByType(Integer typeId, Integer current, String sortBy, Double x, Double y) {
        // 按评分或人气排序（不使用地理位置）
        if ("score".equals(sortBy) || "comments".equals(sortBy)) {
            return Result.ok(query().eq("type_id", typeId)
                    .orderByDesc(StrUtil.isNotBlank(sortBy), "score".equals(sortBy) ? "score" : "comments")
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE)));
        }

        // 默认按距离排序（需要地理位置）
        if (x == null || y == null) {
            return Result.ok(query().eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE)));
        }

        // 计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // Redis GEO 搜索
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> search = stringRedisTemplate.opsForGeo()
                .search(key, GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                                .includeDistance().limit(end));

        if (search == null) {
            return Result.ok(Collections.emptyList());
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = search.getContent();
        if (content.size() <= from) {
            return Result.ok(Collections.emptyList());
        }

        // 解析 ID 列表 + 距离映射
        List<Long> ids = new ArrayList<>();
        Map<String, Distance> distanceMap = new HashMap<>();
        content.stream().skip(from).forEach(result -> {
            String shopId = result.getContent().getName();
            ids.add(Long.valueOf(shopId));
            distanceMap.put(shopId, result.getDistance());
        });

        // 根据 ID 查 DB，按 Redis 顺序返回
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids)
                .last("ORDER BY FIELD(id, " + idStr + ")")
                .list();

        // 设置距离
        for (Shop shop : shops) {
            Distance distance = distanceMap.get(shop.getId().toString());
            if (distance != null) {
                shop.setDistance(distance.getValue());
            }
        }

        return Result.ok(shops);
    }
}