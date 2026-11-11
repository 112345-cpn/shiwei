-- 秒杀 Lua 脚本（原子：判库存 → 判一人一单 → 扣库存 → 记录用户）
--
-- 参数约定（必须与 Java 端一致）：
--   KEYS[1] = 库存 Key        例：seckill:stock:12
--   KEYS[2] = 一人一单集合 Key 例：seckill:order:12
--   ARGV[1] = userId
--   ARGV[2] = orderId（预留，当前脚本不直接使用，订单号由 Java 端生成）
--
-- 返回值：0 = 成功；1 = 库存不足；2 = 重复下单
--
-- 注意：
-- 1. KEYS 必须由调用方通过 keys 列表传入，不能用 ARGV 拼 Key——
--    Redis Cluster 下服务端要根据 KEYS 计算 slot 并做路由校验。
-- 2. 若将来迁移到 Redis Cluster，两个 Key 需落在同一 slot，
--    届时改为 hash tag 写法：seckill:{12}:stock 与 seckill:{12}:order
--    （花括号内内容相同即视为同一 slot），否则会报 CROSSSLOT 错误。

local stockKey = KEYS[1]
local orderKey = KEYS[2]
local userId = ARGV[1]
local orderId = ARGV[2]

-- 1. 判断库存
-- 注意：库存 Key 不存在时 redis.call('get') 返回 false，tonumber(false) 为 nil，
-- 直接比较会报 "attempt to compare nil with number"，所以必须先判 nil
local stock = tonumber(redis.call('get', stockKey))
if (stock == nil or stock <= 0) then
    return 1
end

-- 2. 判断一人一单
if (redis.call('sismember', orderKey, userId) == 1) then
    return 2
end

-- 3. 扣减库存
redis.call('decr', stockKey)

-- 4. 把 userId 存入 set（一人一单防重）
redis.call('sadd', orderKey, userId)

-- 5. 成功返回 0
-- 消息发送由 Java 端通过 RabbitMQ 异步完成，以支持死信队列与重试
return 0
