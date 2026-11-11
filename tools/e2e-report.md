# 黑马点评 E2E 验证报告

- 时间：2026-11-11 15:07:13
- 目标：http://127.0.0.1:8081（Spring Boot 容器 + MySQL/Redis/RabbitMQ）
- 结果：**25 / 25 通过**

| 阶段 | 结论 | 明细 |
| --- | --- | --- |
| P1 服务连通 /shop-type/list | PASS | HTTP 200, success=True, 分类数=10 |
| P2 登录成功 | PASS | token=ddd8326bcf38... |
| P3 建券 + Redis 预加载 | PASS | voucherId=26, DB stock=500, Redis stock=500 |
| P4.1 WS 握手鉴权 | PASS | 首帧={'type': 'connected', 'msg': '连接成功'} |
| P4.2 单次抢购 | PASS | HTTP 200, 耗时 8ms, orderId=658909744836117726 (str) |
| P4.3 Long 序列化为字符串 | PASS | orderId 类型 = str（雪花 ID 超 JS 安全整数） |
| P4.4 WS 秒杀结果推送 | PASS | 收到 {'msg': '抢到了，请在 30 秒内完成支付', 'type': 'seckill_result', 'orderId': '658909744836117726', 'voucherId': '26', 'success': True, 'timestamp': '1794380780537'}（等待 14ms） |
| P4.5 一人一单拦截 | PASS | 第二次抢购 → success=False, msg=不能重复下单 |
| P4.6 库存只扣 1 | PASS | DB stock=499（初始 500） |
| P3 建券 + Redis 预加载 | PASS | voucherId=27, DB stock=500, Redis stock=500 |
| P5 成功数 = 库存 | PASS | 500 成功，期望 500 |
| P5 落库订单数 | PASS | 订单 500 / 成功 500 |
| P5 库存 | PASS | DB stock=0 |
| P5 无负库存（零超卖） | PASS | stock<0 行数 = 0 |
| P5 无重复下单 | PASS | 同用户多单 = 0 |
| P3 建券 + Redis 预加载 | PASS | voucherId=28, DB stock=500, Redis stock=500 |
| P5 落库订单数 | PASS | 订单 194 / 成功响应 194 |
| P5 库存未被限流打穿 | PASS | DB stock=306（洪峰被限流挡住，库存没被打光，符合预期） |
| P5 无负库存（零超卖） | PASS | stock<0 行数 = 0 |
| P5 无重复下单 | PASS | 同用户多单 = 0 |
| P5-b 限流生效（Sentinel 100 QPS） | PASS | 2035 / 2400 请求被限流拦截（85%） |
| P6.1 死信队列自动关单 | PASS | status=4 订单数 = 500 |
| P6.2 无残留未支付单 | PASS | status=1 订单数 = 0 |
| P6.3 数据库库存回补 | PASS | DB stock 回到 500 |
| P6.4 Redis 库存回补 | PASS | Redis stock = 500 |
