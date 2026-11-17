package com.shiwei.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiwei.trade.entity.SeckillVoucher;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {

    /**
     * 预热要扫的券：活动还没结束，且开始时间在 lookAhead 之内。
     * 提前把库存写进 Redis，而不是等第一个人来抢时才写 —— 那样第一个请求要等一次回源。
     *
     * 注意这里取的 stock 是 tb_voucher.stock 而不是 tb_seckill_voucher.stock：
     * 落单时扣的是 tb_voucher，Redis 的初始值必须和它对齐，否则两边一开始就差一截。
     * tb_seckill_voucher.stock 保留作为活动配额的记录，给后台展示「这轮放了多少张」。
     */
    @Select("""
            SELECT sv.voucher_id, sv.begin_time, sv.end_time, v.stock AS stock
            FROM tb_seckill_voucher sv
            JOIN tb_voucher v ON v.id = sv.voucher_id
            WHERE sv.end_time >= NOW()
              AND sv.begin_time <= #{lookAhead}
              AND v.deleted = 0
            """)
    List<SeckillVoucher> selectNeedWarmUp(@Param("lookAhead") LocalDateTime lookAhead);
}
