package com.hmdp;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.Resource;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
public class test {
    @Resource
    public IShopService shopService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Test
    public void Test() throws InterruptedException {
        shopService.saveShop2Redis(1,10L);
    }

    @Test
    public void loadShopData() {
            // 1. 【进货】：查询所有的商铺信息，拿到一个包含所有商铺的大 List。
        Map<Long, List<Shop>> map = shopService.list().stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 2. 【分拣】：把这个大杂烩 List 按照商铺的 typeId 进行分组。
            // 利用 stream().collect(Collectors.groupingBy(...))，按 typeId 分组，得到一个 Map<Long, List<Shop>>
            // 3. 【发货】：遍历这个大柜子（Map）里的每一个抽屉（Entry），分批写进 Redis。
            // 用 entrySet() 开启外层 for 循环遍历 Map
for(Map.Entry<Long, List<Shop>> entry:map.entrySet()){
// ================= 以下是外层 for 循环内部的操作 =================

    // 4. 【拆解抽屉】：拿到当前抽屉的标签（typeId），并拼装出存入 Redis 的最终 Key。
    // 获取 entry 的 Key，拼接成类似 "shop:geo:1" 的字符串
    Long typeId = entry.getKey();
String key = "shop:geo:" + typeId;
    // 5. 【拿货】：拿到当前抽屉里的货物，也就是同属这个分类的商铺 List。
    // 获取 entry 的 Value，得到 List<Shop>
    List<Shop> value = entry.getValue();

    // 6. 【准备打包盒】：创建一个专门装 GeoLocation 的集合。
    // new 一个 List<RedisGeoCommands.GeoLocation<String>>
    // 【关键点】：千万记得把第 5 步拿到的货物集合的 size() 作为初始容量传进去，锁死容量！
    List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
    // 7. 【货物装箱】：遍历刚才拿到的那一批商铺 List，把经纬度塞进打包盒。
    // 开启内层 for 循环，遍历 List<Shop>
    for(Shop shop:value){
// ================= 以下是内层 for 循环内部的操作 =================

        // 7.1 把当前商铺的 ID 变成 String。
        // 7.2 获取经度（X）和纬度（Y），塞进 new Point(x, y) 对象中。
        // 7.3 把 ID 和 Point 组合成 new RedisGeoCommands.GeoLocation<>(id, point)。
        // 7.4 把装好的 GeoLocation 丢进第 6 步准备好的“打包盒”集合里。
        locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),new Point(shop.getX(), shop.getY())));
    }
    // 8. 【一键发射】：把拼装好的 Key 和装满的打包盒，一次性发给 Redis。
    // 调用 stringRedisTemplate.opsForGeo().add(key, 包装盒集合) 进行批量添加

stringRedisTemplate.opsForGeo().add(key, locations);
    // ================= 外层 for 循环结束 =================
}




    }
}
