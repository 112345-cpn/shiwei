package com.shiwei.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiwei.review.entity.Review;
import com.shiwei.review.vo.RatingStatRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ReviewMapper extends BaseMapper<Review> {

    /**
     * 一个门店的全部评分统计，一次扫描出结果。
     * SUM(条件) 在 MySQL 里返回 decimal，显式 CAST 成 SIGNED
     * 否则映射到 Integer 会报类型转换错。
     */
    @Select("""
            SELECT COUNT(*)                                              AS review_count,
                   IFNULL(AVG(score_total), 0)                           AS avg_total,
                   IFNULL(AVG(score_taste), 0)                           AS avg_taste,
                   IFNULL(AVG(score_env), 0)                             AS avg_env,
                   IFNULL(AVG(score_service), 0)                         AS avg_service,
                   CAST(SUM(score_total = 1) AS SIGNED)                  AS star1,
                   CAST(SUM(score_total = 2) AS SIGNED)                  AS star2,
                   CAST(SUM(score_total = 3) AS SIGNED)                  AS star3,
                   CAST(SUM(score_total = 4) AS SIGNED)                  AS star4,
                   CAST(SUM(score_total = 5) AS SIGNED)                  AS star5
            FROM tb_review
            WHERE shop_id = #{shopId} AND status = 1 AND deleted = 0
            """)
    RatingStatRow selectStat(@Param("shopId") Long shopId);

    /**
     * 有用数用自增而不是「读出来 +1 再写回去」，避免并发丢更新。
     */
    @Update("UPDATE tb_review SET useful_count = useful_count + 1 WHERE id = #{id} AND deleted = 0")
    int incrUseful(@Param("id") Long id);
}
