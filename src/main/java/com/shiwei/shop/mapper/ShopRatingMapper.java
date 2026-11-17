package com.shiwei.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiwei.review.vo.RatingStatRow;
import com.shiwei.shop.entity.ShopRating;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ShopRatingMapper extends BaseMapper<ShopRating> {

    /**
     * 聚合成败都幂等：已经有行就覆盖，没有就插。
     * 用 ON DUPLICATE KEY 而不是「先查再决定 insert/update」，
     * 两个消费者同时处理同一家店时不会撞主键。
     */
    @Insert("""
            INSERT INTO tb_shop_rating
                (shop_id, review_count, avg_total, avg_taste, avg_env, avg_service,
                 star1, star2, star3, star4, star5, update_time)
            VALUES
                (#{shopId}, #{s.reviewCount}, #{s.avgTotal}, #{s.avgTaste}, #{s.avgEnv}, #{s.avgService},
                 #{s.star1}, #{s.star2}, #{s.star3}, #{s.star4}, #{s.star5}, NOW())
            ON DUPLICATE KEY UPDATE
                review_count = VALUES(review_count),
                avg_total    = VALUES(avg_total),
                avg_taste    = VALUES(avg_taste),
                avg_env      = VALUES(avg_env),
                avg_service  = VALUES(avg_service),
                star1        = VALUES(star1),
                star2        = VALUES(star2),
                star3        = VALUES(star3),
                star4        = VALUES(star4),
                star5        = VALUES(star5),
                update_time  = NOW()
            """)
    int upsert(@Param("shopId") Long shopId, @Param("s") RatingStatRow stat);
}
