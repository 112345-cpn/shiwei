package com.shiwei.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shiwei.trade.entity.Refund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface RefundMapper extends BaseMapper<Refund> {

    /**
     * 审核结果落库，带 status = 0 前置条件。
     * 商家后台双击提交、或两个店员同时点「同意」，只有一次能改成功，
     * 否则会重复退款、重复回滚库存。
     */
    @Update("""
            UPDATE tb_refund
            SET status = #{to},
                audit_staff_id = #{staffId},
                audit_time = #{auditTime},
                finish_time = #{finishTime}
            WHERE id = #{id} AND status = 0
            """)
    int markAudited(@Param("id") Long id,
                    @Param("to") int to,
                    @Param("staffId") Long staffId,
                    @Param("auditTime") LocalDateTime auditTime,
                    @Param("finishTime") LocalDateTime finishTime);

    /*
     * 退款单表里只有 order_id，查「我的退款」「我商家的退款」必须回联订单表。
     * 写成 join 而不是 inSql 拼子串：拼串等于把 SQL 片段当字符串传，
     * 一旦哪天参数从 Long 变成可输入的东西就是注入口子。
     */

    @Select("""
            SELECT r.* FROM tb_refund r
            JOIN tb_order o ON o.id = r.order_id
            WHERE o.user_id = #{userId}
            ORDER BY r.id DESC
            """)
    IPage<Refund> pageByUser(IPage<Refund> page, @Param("userId") Long userId);

    @Select("""
            SELECT r.* FROM tb_refund r
            JOIN tb_order o ON o.id = r.order_id
            WHERE o.merchant_id = #{merchantId}
              AND (#{status} IS NULL OR r.status = #{status})
            ORDER BY r.id DESC
            """)
    IPage<Refund> pageByMerchant(IPage<Refund> page,
                                 @Param("merchantId") Long merchantId,
                                 @Param("status") Integer status);

    @Select("""
            SELECT COUNT(*) FROM tb_refund r
            JOIN tb_order o ON o.id = r.order_id
            WHERE o.merchant_id = #{merchantId} AND r.status = 0
            """)
    long countPending(@Param("merchantId") Long merchantId);
}
