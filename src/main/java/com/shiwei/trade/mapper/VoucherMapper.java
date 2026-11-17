package com.shiwei.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiwei.trade.entity.Voucher;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface VoucherMapper extends BaseMapper<Voucher> {

    /**
     * 扣库存。防超卖不靠 Java 的 if 判断，靠这条 SQL 的 where 条件：
     * 并发时数据库行锁会把并发的 update 串行化，stock >= n 不成立的那次影响行数是 0。
     * 返回 0 就是没抢到，调用方据此抛 VOUCHER_STOCK_SHORT。
     */
    @Update("""
            UPDATE tb_voucher
            SET stock = stock - #{n}, sold = sold + #{n}
            WHERE id = #{id} AND stock >= #{n} AND deleted = 0
            """)
    int deductStock(@Param("id") Long id, @Param("n") int n);

    /**
     * 回滚库存。关单、退款时用，同时把 sold 减回去，否则「已售」会一直虚高。
     * 不加 stock >= n 的条件：回滚是善后动作，不能因为库存对不上就失败。
     */
    @Update("""
            UPDATE tb_voucher
            SET stock = stock + #{n}, sold = GREATEST(sold - #{n}, 0)
            WHERE id = #{id} AND deleted = 0
            """)
    int restoreStock(@Param("id") Long id, @Param("n") int n);
}
