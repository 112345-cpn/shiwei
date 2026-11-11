package com.hmdp.controller;


import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("list")
    public Result queryTypeList() {
        String shopType = (String) stringRedisTemplate.opsForValue().get("shopType");
        if(StrUtil.isNotBlank(shopType)) {
           List<ShopType>    shopType1= JSONUtil.toList(JSONUtil.parseArray(shopType), ShopType.class);
            return Result.ok(shopType1);
        }
        List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
        if(typeList== null){
            return Result.fail("没有数据");
        }
      stringRedisTemplate.opsForValue().set("shopType",JSONUtil.toJsonStr(typeList));
        return Result.ok(typeList);
    }
}
