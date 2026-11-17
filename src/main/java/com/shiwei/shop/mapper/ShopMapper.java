package com.shiwei.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiwei.shop.entity.Shop;
import com.shiwei.shop.vo.ShopNearbyRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ShopMapper extends BaseMapper<Shop> {

    /**
     * 把聚合结果回写到门店主表，供列表排序直接使用，
     * 避免每页都去 join 聚合表算一次。
     */
    @Update("UPDATE tb_shop SET rating = #{rating}, rating_count = #{count} WHERE id = #{shopId} AND deleted = 0")
    int updateRating(@Param("shopId") Long shopId,
                     @Param("rating") java.math.BigDecimal rating,
                     @Param("count") int count);

    /**
     * 重算门店销量。取的是「已支付 + 已核销」的单量，
     * 用重算而不是 +1：支付成功消息可能被重复投递，
     * 累加会把销量算高，重算永远是同一个结果。
     */
    @Update("""
            UPDATE tb_shop s
            SET s.sold = (
                SELECT IFNULL(SUM(o.quantity), 0) FROM tb_order o
                WHERE o.shop_id = s.id AND o.status IN (1, 2)
            )
            WHERE s.id = #{shopId}
            """)
    int refreshSold(@Param("shopId") Long shopId);

    /**
     * 附近门店。用球面余弦的近似式算距离，范围小的时候误差可以忽略。
     * 走 (x,y) 联合索引先框一个矩形，再按距离排序，避免全表扫。
     */
    @Select("""
            SELECT s.id, s.name, s.type_id, s.images, s.area, s.address,
                   s.avg_price, s.rating, s.rating_count, s.x, s.y,
                   (6371000 * ACOS(
                       LEAST(1, COS(RADIANS(#{lat})) * COS(RADIANS(s.y)) *
                                 COS(RADIANS(s.x) - RADIANS(#{lng})) +
                              SIN(RADIANS(#{lat})) * SIN(RADIANS(s.y)))
                   )) AS distance
            FROM tb_shop s
            WHERE s.deleted = 0 AND s.status = 0
              AND s.x BETWEEN #{lng} - #{dLng} AND #{lng} + #{dLng}
              AND s.y BETWEEN #{lat} - #{dLat} AND #{lat} + #{dLat}
            ORDER BY distance ASC
            LIMIT #{limit}
            """)
    List<ShopNearbyRow> selectNearby(@Param("lat") double lat,
                                     @Param("lng") double lng,
                                     @Param("dLat") double dLat,
                                     @Param("dLng") double dLng,
                                     @Param("limit") int limit);
}
