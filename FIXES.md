# 黑马点评 —— 修补记录

> 两轮改动：
> **第一轮**修后端的"逻辑对不对"（6 处，其中 1 处是那种"代码能跑、测试能过、但逻辑是错的"）；
> **第二轮**换前端 + 补三个配套问题（上传路径、ID 精度、验证码），让项目能演示给人看。
>
> 环境：Spring Boot 3.3.5 / JDK 17 / MySQL 8 / Redis 7 / RabbitMQ 3 / nginx 1.18

---

# 第一轮：后端逻辑修补（2026-11-09）

## 结论先行

| # | 问题 | 严重程度 | 一句话 |
|---|---|---|---|
| 1 | **消费端调本类事务方法，事务从未生效** | 致命 | 走不到 Spring 代理，`@Transactional` 等于没写 |
| 2 | WebSocket 通道建好但业务没调用 | 高 | 类都在，`NotificationService` 全项目零引用 |
| 3 | 三套限流里两套是空架子 | 中 | `@IpRateLimiter` 定义了但没人用，防刷拦截器未登录直接放行 |
| 4 | 两套 MQ 队列名并存且不一致 | 中 | yaml 那套是死的，实际生效的是代码常量 |
| 5 | 防刷用固定窗口，边界可双倍穿透 | 低 | 知道就行，改滑动窗口成本更高 |
| 6 | Sentinel 控制台地址三处不一致 | 低 | 8080 / 8081 / 8858 对不上，控制台连不上 |

---

## 1. 消费端调本类事务方法，事务一直没生效（最严重）

### 现象

秒杀下单流程"看起来"正常：订单能创建、库存能扣。但只要中间抛异常，
已经写入的数据不会回滚，还出现过"订单建了、库存没扣"的脏数据。

### 根因

```java
// 同一个类里
@RabbitListener(queues = "hmdp.seckill.order")
public void onMessage(SeckillOrderMessage message) {
    asyncCreateVoucherOrder(message);   // ← 自调用
}

@Transactional
public void asyncCreateVoucherOrder(SeckillOrderMessage message) { ... }
```

`@Transactional` 是**基于代理**的：Spring 给这个 Bean 生成代理对象，只有从外部
通过代理调用才会织入事务逻辑。`this.asyncCreateVoucherOrder(...)` 直接走原始对象，
绕过了代理，**事务注解被完全忽略**。

这是"代码能跑、测试能过、但没有事务"的典型，只看注解根本发现不了。

### 改法

注入自身代理（`@Lazy` 打破循环依赖）：

```java
private IVoucherOrderService self;

@Autowired
@Lazy
public void setSelf(IVoucherOrderService self) {
    this.self = self;
}
```

调用处改成走代理：

```java
self.asyncCreateVoucherOrder(message);
```

### 面试怎么讲

> 事务注解失效是因为自调用绕过了代理。解决方式有三种：
> 注入自身代理、`AopContext.currentProxy()`（需要开 `exposeProxy`）、
> 或者把方法拆到另一个 Bean。我选了注入自身代理，因为它最显式、不依赖 AOP 配置。

---

## 2. WebSocket 通道建好，但业务从来没调用

### 现象

WebSocket 相关类一应俱全（`NotificationWebSocketHandler`、`WebSocketSessionManager`、
`TokenHandshakeInterceptor`、`WebSocketConfig`），端点 `/ws/notification` 也能连上、能收到欢迎消息。
但秒杀成功/失败，前端什么都收不到。

### 根因

`NotificationService` 在整个项目里**零调用**。通道是通的，没有人往里发东西。
典型的"框架搭好了，业务没接"。

### 改法

在 `VoucherOrderServiceImpl` 里补推送，并且注意顺序：

```java
// 3. 到这里事务已提交，再推送 —— 顺序很重要。
//    如果先推后提交，事务回滚时用户会收到"抢到了"但订单不存在。
pushSeckillResult(message, true);
```

推送本身不能影响主流程（订单已经建好了，推送只是通知）：

```java
private void pushSeckillResult(SeckillOrderMessage message, boolean success) {
    try {
        Map<String, Object> payload = new HashMap<>(6);
        payload.put("type", "seckill_result");
        payload.put("orderId", message.getOrderId());
        payload.put("voucherId", message.getVoucherId());
        payload.put("success", success);
        payload.put("msg", success ? "抢到了，请在 30 秒内完成支付" : "很遗憾，下单失败");
        payload.put("timestamp", System.currentTimeMillis());
        notificationService.pushToUser(message.getUserId(), payload);
    } catch (Exception e) {
        log.warn("秒杀结果推送失败，不影响订单：orderId={}", message.getOrderId(), e);
    }
}
```

### 为什么用 WebSocket 而不是轮询

| | 轮询 | WebSocket |
|---|---|---|
| 请求量 | 1 万在线用户 × 每秒 1 次 = 1 万 QPS 空转 | 长连接，只在有结果时推 |
| 延迟 | 平均半个轮询周期 | 毫秒级 |
| 服务器成本 | 大量无效查询打 DB/Redis | 只占连接数 |

秒杀场景下轮询会让"本来就该削峰"的系统再挨一次无效流量，这个问题在 Go 推送服务里
用压测验证过：单机 5000 长连接内存占用和推送 P99 都在可接受范围。

---

## 3. 三套限流，两套是空架子

### 现状核查

| 组件 | 定义 | 实际使用 |
|---|---|---|
| Sentinel `FlowRule`（seckill/blog/login 三条） | `SentinelConfig` 已注册 | 只有 seckill 被 `@RateLimiter` 真正调用 |
| 自定义 `@RateLimiter` | 注解 + AOP 切面都有 | seckill、blog、login 三处 |
| 自定义 `@IpRateLimiter` | 注解 + 切面都有 | **全项目零使用** |
| `AntiBrushInterceptor` | 注册在 `/blog/**`、`/follow/**`、`/sign`、秒杀入口 | 生效，但未登录直接放行 |

一个"看起来有三层限流"的项目，实际只有一层半在工作。

### 改法

**（1）验证码接口按 IP 限流**——这个接口调用时用户还没登录，只能按 IP：

```java
@IpRateLimiter(name = "ip:user.sendCode", qps = 1, message = "验证码请求过于频繁，请稍后再试")
@PostMapping("code")
public Result sendCode(@RequestParam("phone") String phone, HttpSession session) { ... }
```

**（2）登录做两层**——全局防撞库，IP 级防单机脚本：

```java
@RateLimiter(name = "login", qps = 10, message = "登录请求过于频繁，请稍后再试")
@IpRateLimiter(name = "ip:user.login", qps = 5, message = "登录请求过于频繁，请稍后再试")
@PostMapping("/login")
public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session) { ... }
```

**（3）发笔记限流**：`@RateLimiter(name = "blog", qps = 20)`。

**（4）防刷拦截器未登录时改按 IP 计**：

```java
// 原来：未登录直接放行 —— 等于关掉了这道防线，脚本不登录就能刷
// 改后：没有用户 ID 就用 IP 做维度
```

### 为什么验证码要按 IP 限而不是按用户

用户此时还没登录，拿不到 userId；而这个接口每调一次就是一条真金白银的短信。
按 IP 限到 1 QPS，足以挡住脚本，正常人也不会 1 秒点一次。

---

## 4. 两套 MQ 队列名并存，yaml 那套是死的

### 现象

`application.yaml` 里有一套 `hmdp.rabbitmq.exchange / queue / routing-key`；
`RabbitMQConfig` 里又用常量定义了一套，两边名字不一致。
排查消息去向时按 yaml 找会找不到队列。

### 根因

实际生效的是代码里的常量（`@RabbitListener(queues = RabbitMQConfig.QUEUE)`），
yaml 那套从来没被任何代码读取过 —— 属于"看起来很完整的死配置"。

### 改法

删掉 yaml 里那几行，并在原位留注释说明真正的定义在 `RabbitMQConfig` 常量里：

```yaml
# 注意：RabbitMQ 的交换机 / 队列 / 路由键名统一定义在 RabbitMQConfig 的常量里
# （hmdp.seckill.exchange / hmdp.seckill.order / hmdp.seckill.order.dl）。
# 这里不再重复配置 —— 原来两处各写一套且名字不一致，实际生效的是代码常量，
# yaml 那套是死的，容易在面试或排查时答错。
```

---

## 5. 防刷是固定窗口，边界能双倍穿透

`AntiBrushInterceptor` 用 `Redis INCR + EXPIRE` 实现，是**固定窗口**：

```
窗口 1（00:00–00:59）限额 10
窗口 2（01:00–01:59）限额 10

攻击者在 00:59 打 10 次，01:00 再打 10 次 —— 两秒内 20 次，但每个窗口都没超
```

### 改法（本轮只做了注释说明，没改实现）

- 把注释里"滑动窗口"的说法改成"固定窗口"，并写明边界穿透问题；
- 真要解决，用 Redis ZSet 存每次请求时间戳、`ZREMRANGEBYSCORE` 清掉窗口外的，
  或者直接用 Sentinel 的滑动窗口。成本比 INCR 高一个量级，当前流量下不值。

**取舍**：这类问题面试常问，答"我知道它是固定窗口、能穿透，但当前 QPS 下不值得换"比
"我以为是滑动窗口"要好得多。

---

## 6. Sentinel 控制台地址三处对不上

`SentinelConfig` 里写 `localhost:8080`，应用自己监听 8081，docker-compose 里控制台在 8858。
三处不一致，结果控制台永远显示"应用未连接"。

### 改法

统一成 `localhost:8858`，并改成可被环境变量覆盖：

```yaml
sentinel:
  transport:
    dashboard: ${SENTINEL_DASHBOARD:localhost:8858}
```

顺手把生产配置的 SQL 日志关掉：

```yaml
# application-prod.yaml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl
```

主配置是 `StdOutImpl`，每条 SQL 同步写控制台 —— 压测时日志本身就是瓶颈，
测出来的 QPS 没有参考价值（这一点在 Go 推送服务上实测过：控制台输出让 P50 从 2.7ms 涨到 10.9ms）。

---

## 第一轮的验证方式

```powershell
# 1. 起依赖
docker compose up -d mysql redis rabbitmq

# 2. 编译
mvn -q clean package -DskipTests

# 3. 起服务
java -jar target/*.jar

# 4. 浏览器打开 http://localhost:8080 ，登录后进任意商铺详情页秒杀
#    预期：点「立即抢」→ 2 秒内弹出「抢到了」，并且数据库里能查到该订单
```

限流验证：

```powershell
1..20 | ForEach-Object { curl.exe -s -X POST http://localhost:8081/voucher-order/seckill/10 }
```

---

## 第一轮还没解决的问题

| 问题 | 影响 | 建议 |
|---|---|---|
| **README 的性能数字没有支撑** | "800→5000+ QPS""40x"面试一问就穿 | 用 JMeter（`D:\apache-jmeter-5.6.3`）压一次，换成真实数据 |
| `publisher-confirm-type` 配了但没回调 | 消息可靠投递只配了一半 | 加 `ConfirmCallback` + `ReturnsCallback` |
| 死信队列的延迟靠消息级 TTL | 不同 TTL 混在同一队列会队头阻塞 | 当前 TTL 相同，暂时没问题 |
| `@RateLimiter` 和 `@IpRateLimiter` 同标一个方法 | 两个切面都生效，拦截顺序不确定 | 目前不冲突，够用 |

---

## 第一轮在面试里怎么讲（按含金量排）

| 序 | 讲什么 | 为什么值钱 |
|---|---|---|
| 1 | **发现消费端调事务方法没走代理，事务一直没生效** | 这是"看得懂框架"和"只会写注解"的分水岭 |
| 2 | WebSocket 通道建好但业务没接，补上推送并处理"先提交后推送"的顺序 | 说明你考虑了一致性，不是"能跑就行" |
| 3 | 三套限流里两套是空架子，补上并说清为什么验证码要按 IP 限 | 说明你会判断"这段代码到底生不生效" |
| 4 | 两套队列名冲突，实际生效的和文档写的不一样 | 说明你核对过配置，不是照抄 |
| 5 | 防刷未登录直接放行 = 形同虚设 | 安全意识 |

**最值钱的是第 1 条**。因为它属于"代码能跑、测试能过、但逻辑是错的"那类问题，
只有真正理解 Spring 代理机制才看得出来。

---
---

# 第二轮：前端重做 + 配套修补（2026-11-10）

第一轮修的是后端的"逻辑对不对"，这一轮修的是"能不能演示给人看"。
前端整个换掉，顺手挖出三处必须一起改的后端/运维问题。

---

## 1. 前端整套重做

### 为什么必须重做

原前端是 Vue2 + Element UI（2022 年的版本），三个问题：

1. **一眼看去就是教学项目**：标题写着"黑马点评"，蓝白 Element 配色，页面样式和现在任何一个真实产品都不像。
2. **接口调用和渲染逻辑混在 HTML 的 `v-for` 里**，改一个字段要改三处，也没法解释"这个页面数据从哪来"。
3. **没有承接 WebSocket**：上一轮后端已经能把秒杀结果推出来了，前端没有接收代码，推了就丢。

### 新前端的技术选择

| 决策 | 选择 | 理由 |
|---|---|---|
| 框架 | **不用框架，原生 HTML/CSS/JS** | 零依赖，不引入 npm/构建步骤，nginx 直接托管；面试时每个文件都能讲清 |
| 页面形态 | 多页面（MPA），7 个 html | 和原项目「一个页面一个链接」的结构一致，不需要改 nginx 路由 |
| 状态管理 | 一个 `API` 模块 + 各页面局部 `state` 对象 | 这个体量不需要 Vuex/Pinia |
| 接口层 | `fetch` 封装，统一解包 `Result` | 页面里不出现 fetch 细节，401 统一跳登录 |
| 样式 | 单文件设计系统 `css/app.css` | 改品牌色只改 `:root` 里的 `--brand` |

**代码量**：CSS 约 1100 行，JS 约 700 行，7 个页面。

### 页面清单

| 文件 | 页面 | 关键点 |
|---|---|---|
| `index.html` | 发现 / 店铺列表 | 分类切换、三种排序、搜索、加载更多、骨架屏 |
| `shop.html` | 店铺详情 + 秒杀 | **实时抢购台**：展示 WS 连接状态 + 这次请求的完整链路时间线 |
| `login.html` | 登录 | 验证码倒计时、开发环境自动回填 |
| `notes.html` | 笔记流 | 热门 / 关注 / 我的三个 tab，关注流用滚动分页 |
| `note.html` | 笔记详情 | 点赞乐观更新、关注按钮、点赞用户头像 |
| `note-edit.html` | 发笔记 | 多图上传（最多 9 张）、第一张自动当封面 |
| `me.html` | 个人中心 | 连续签到、笔记数、WS 连接状态、退出登录 |

### 品牌与视觉

- 品牌名 **拾味 SHIWEI**，全部页面无「点评」字样（HTML、title、meta、页脚均已替换）。
- 配色：暖白底 `#f7f5f2` + 墨黑文字 `#191512` + 珊瑚橘 `#ff4e22` 强调。
- 细节：毛玻璃导航、16:10 卡片图、悬停微抬升、骨架屏、图片加载失败降级成"首字渐变块"（店铺图是外链 CDN，会挂）。

### 前端文件结构

```
html/hmdp/
├── index.html          发现页
├── shop.html           店铺详情 + 秒杀
├── notes.html          笔记流
├── note.html           笔记详情
├── note-edit.html      发笔记
├── login.html          登录
├── me.html             个人中心
├── css/app.css         设计系统（单文件）
├── js/api.js           接口层 + token + WebSocket 自动重连
├── js/ui.js            导航 / Toast / 图片兜底 / 秒杀弹层
├── imgs/               分类图标、默认头像（沿用原资源）
└── hmdp-legacy/ ←     旧前端整份备份（在 html/ 下，不对外）
```

### 接口约定（新前端沿用，未改后端接口）

- 所有请求走 `/api` 前缀，由 nginx `rewrite /api(/.*) $1` 转发到 8081。
- token 放请求头 `authorization`，存 `localStorage['shiwei_token']`。
- 响应体 `{success, errorMsg, data, total}`，`success === false` 一律按失败处理。
- WebSocket：`ws://<host>/ws/notification?token=<token>`，消息体 `{type, ...}`。

---

## 2. nginx 没有 `/ws` 代理（致命，但很难察觉）

### 现象

前端连 WebSocket 一直失败，控制台报 `WebSocket connection to 'ws://localhost:8080/ws/notification' failed`，
但浏览器直接连 `ws://localhost:8081/ws/notification` 是通的。

### 根因

`conf/nginx.conf` 里只有 `location /api`，没有 `location /ws`。
WebSocket 握手需要 `Upgrade` / `Connection` 头透传，nginx 默认不转发，握手直接被当成普通静态请求处理。

### 改法

```nginx
location /ws {
    proxy_pass http://127.0.0.1:8081;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_read_timeout 3600s;   # 默认 60s 会掐断长连接
    proxy_send_timeout 3600s;
}
```

### 怎么验证

打开 `shop.html`，右侧「实时抢购台」的连接指示灯是绿的、显示「已连接」，
说明 nginx 的 Upgrade 透传生效了，浏览器控制台也能看到 `{"type":"connected"}`。

---

## 3. 上传目录写死在 E 盘

### 现象

发笔记上传图片报 `RuntimeException: 文件上传失败`，异常里是 `IOException: 系统找不到指定的路径`。

### 根因

```java
// SystemConstants.java
public static final String IMAGE_UPLOAD_DIR = "E://nginx-1.18.0//html//hmdp//imgs";
```

绝对路径写死在代码里。项目放到别的盘、nginx 装到别的位置，上传就废。
而且报错是 `IOException`，第一反应会去查磁盘权限，很难想到是路径常量。

### 改法

改成配置项，默认值用相对路径（相对后端启动目录）：

```yaml
# application-dev.yaml
hmdp:
  upload:
    dir: ./nginx-1.18.0/html/hmdp/imgs
```

```java
@Value("${hmdp.upload.dir:./nginx-1.18.0/html/hmdp/imgs}")
private String uploadDir;
```

`SystemConstants.IMAGE_UPLOAD_DIR` 标记 `@Deprecated` 并写明原因，避免有人再用。

---

## 4. 雪花 ID 按数字序列化，前端会丢精度

### 现象

秒杀成功后页面显示的订单号和数据库里的对不上，末几位被抹成了 0。

### 根因

- 雪花算法生成的订单 ID 是 19 位（约 `1.9×10^18`）。
- JavaScript 的 `Number` 只能精确表示到 `2^53-1`（约 `9×10^15`）。
- Jackson 默认把 `Long` 序列化成 JSON 数字，前端 `JSON.parse` 之后精度就没了。

表现是：**前端拿到的订单号最后几位永远是 0**，用户拿着这个号去查单查不到。

### 改法

新建 `config/JacksonConfig.java`，全局把 `Long` / `long` / `BigInteger` 序列化成字符串：

```java
SimpleModule module = new SimpleModule();
module.addSerializer(Long.class, ToStringSerializer.instance);
module.addSerializer(Long.TYPE, ToStringSerializer.instance);
builder.modulesToInstall(module);
```

Spring 反序列化时能把 `"123"` 正确还原成 `Long`，所以接口参数不受影响。

### 连带影响

`Result.total` 也变成字符串了，前端分页比较时要用 `Number(total)`。
`js/api.js` 的 `normalizePage()` 里已经统一转换。

---

## 5. 开发环境拿不到验证码，登录不了

### 现象

点了「获取验证码」，页面提示成功，但手机上没短信、页面上没显示、日志里也搜不到 —— 无法登录。

### 根因（两层）

1. 验证码只 `log.debug("验证码：{}", code)`，不返回给前端；
2. `application-dev.yaml` 里 `logging.level.com.hmdp.service: info`，把 service 包的 debug 压掉了 —— **所以连日志都没有**。

### 改法

加开关 `hmdp.auth.expose-code`，开发环境打开，验证码直接返回给前端自动填入：

```yaml
# application-dev.yaml
hmdp:
  auth:
    expose-code: true     # 生产必须 false
```

```yaml
# application-prod.yaml
hmdp:
  auth:
    expose-code: false
```

打开时同时用 `log.info` 打一行，带上"生产环境请关闭"的提示。

---

## 这次改动的自检

| 检查项 | 结果 |
|---|---|
| 7 个页面的内联 JS 语法 | 全部通过 `node --check` |
| `api.js` / `ui.js` 语法 | 通过 |
| `nginx -t` 配置校验 | successful |
| 后端 4 个改动文件编译 | JDK 17 编译通过 |
| 页面实际渲染（mock 数据） | 首页 / 详情页 / 登录页渲染正常，图片兜底生效 |
| WebSocket 推送链路 | 待后端起来后验证（需 Docker 起 Redis + RabbitMQ） |

**注意**：这一轮的前端验证用的是 mock 接口（`mock-api.py`，按真实接口结构造数据），
目的是确认布局和渲染逻辑，不代表后端业务逻辑已验证。后端要跑起来仍需 MySQL + Redis + RabbitMQ。

---

## 部署时的两个坑（写给换机器的人）

1. **nginx 不能放在中文路径下。**
   放在 `D:/.../资料/hm-dianping/nginx-1.18.0` 时启动直接报
   `CreateFile() failed (1113: No mapping for the Unicode character exists...)`。
   nginx for Windows 用的是 ANSI 路径 API，中文目录会让它找不到配置文件。
   **要么把 nginx 挪到纯英文路径，要么整个项目别放中文目录。** 当前用的是 `E:/nginx-1.18.0`。

2. **改完 `conf/nginx.conf` 要重载才生效。**
   `nginx -s reload`（Windows 下在 nginx 目录执行 `.\nginx.exe -s reload`）。
   只重启 `java -jar` 不改 nginx，`/ws` 代理永远不会生效。
