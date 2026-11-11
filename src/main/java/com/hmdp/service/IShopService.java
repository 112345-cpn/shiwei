package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;

/**
 * 商户服务接口
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据 ID 查询商户（缓存三连：逻辑过期方案）
     */
    Result queryById(Long id);

    /**
     * 更新商户（Cache-Aside 模式：先 DB 后缓存）
     */
    Result update(Shop shop);

    /**
     * 缓存预热（启动时调用）
     */
    void warmUpCache();

    /**
     * 根据类型查询附近商户（支持排序）
     * @param typeId 商户类型ID
     * @param current 当前页
     * @param sortBy 排序字段（空=默认，comments=人气，score=评分）
     * @param x 经度
     * @param y 纬度
     */
    Result queryShopByType(Integer typeId, Integer current, String sortBy, Double x, Double y);
}
