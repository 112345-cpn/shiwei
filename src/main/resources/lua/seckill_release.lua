-- 归还秒杀名额。关单、退款时调用。
--
-- 必须先判 key 是否存在：对不存在的 key 做 INCR 会凭空创建一个 stock=1 的键，
-- 活动结束后这张券就永远多出一张「幽灵库存」，卖出去就是超卖。
--
-- KEYS[1] = 库存 key
-- KEYS[2] = 已购用户集合 key
-- ARGV[1] = 用户 id
--
-- 返回：1 归还成功 / 0 活动已收尾，无需处理

if redis.call('EXISTS', KEYS[1]) == 0 then
    return 0
end

redis.call('INCR', KEYS[1])
redis.call('SREM', KEYS[2], ARGV[1])

return 1
