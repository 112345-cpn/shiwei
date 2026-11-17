package com.shiwei.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiwei.trade.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /*
     * 以下方法全部是「带前置状态的条件更新」而不是「先查再改」。
     * 原因：查完再改中间有窗口，两个请求可以都读到 status=0 然后都往下走。
     * 条件更新把判断交给数据库，影响行数为 0 就说明别人先动了，直接按冲突处理。
     */

    @Update("""
            UPDATE tb_order SET status = 1, pay_time = #{payTime}
            WHERE id = #{id} AND status = 0
            """)
    int markPaid(@Param("id") Long id, @Param("payTime") LocalDateTime payTime);

    @Update("""
            UPDATE tb_order
            SET status = 2, verify_time = #{verifyTime}, verify_staff_id = #{staffId}
            WHERE id = #{id} AND status = 1
            """)
    int markVerified(@Param("id") Long id,
                     @Param("verifyTime") LocalDateTime verifyTime,
                     @Param("staffId") Long staffId);

    @Update("UPDATE tb_order SET status = 3 WHERE id = #{id} AND status = 1")
    int markRefunding(@Param("id") Long id);

    @Update("UPDATE tb_order SET status = 4, refund_time = #{refundTime} WHERE id = #{id} AND status = 3")
    int markRefunded(@Param("id") Long id, @Param("refundTime") LocalDateTime refundTime);

    /** 商家驳回退款，订单退回待使用。 */
    @Update("UPDATE tb_order SET status = 1 WHERE id = #{id} AND status = 3")
    int rollbackToPendingUse(@Param("id") Long id);

    /** 超时关单。只有待支付的单能关。 */
    @Update("UPDATE tb_order SET status = 5 WHERE id = #{id} AND status = 0")
    int markClosed(@Param("id") Long id);

    /** 定时兜底用：捞出已经过了支付截止时间但还挂着的单。 */
    @Select("""
            SELECT id FROM tb_order
            WHERE status = 0 AND expire_time < NOW()
            ORDER BY id
            LIMIT #{limit}
            """)
    List<Long> selectTimeoutIds(@Param("limit") int limit);
}
