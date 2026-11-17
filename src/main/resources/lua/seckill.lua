-- 秒杀预扣。整段在 Redis 里原子执行，不存在「查完库存再扣」的窗口。
--
-- KEYS[1] = 库存 key，String，值是剩余张数
-- KEYS[2] = 已购用户集合 key，Set
-- ARGV[1] = 用户 id
--
-- 返回：0 抢到 / 1 售罄 / 2 重复下单 / 3 活动未预热

if redis.call('EXISTS', KEYS[1]) == 0 then
    return 3
end

if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return 2
end

local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil or stock <= 0 then
    return 1
end

redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])

return 0
