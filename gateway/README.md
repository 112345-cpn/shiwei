# WebSocket 网关

纯 Go 写的一个小服务，只有一个职责：把 Java 推过来的事件送到对应的浏览器。

```
浏览器 ──WS──> 网关 ──订阅── RabbitMQ <──发布── Java 业务
```

网关不连数据库、不写业务逻辑，也不存会话 —— 鉴权直接问 Java 的 `/api/auth/me`，保证"谁是登录用户"只有一处真相。

## 先说实话

**在日活几十人的 demo 里，Spring 自带的 WebSocket 完全够用，拆这个服务是过度设计。**

拆出来的理由是学习性的：把长连接能力和业务解耦，同时用 Go 单独练一段服务。面试被问到要这么答，别包装成"因为并发高"。

真要说技术上的合理性，只有一条：Go 一个 goroutine 几 KB，扛长连接比 JVM 线程模型省得多。连接数上到万级时这个差别才成立。

## 跑起来

```bash
cd gateway
go run .
```

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `GATEWAY_PORT` | 8082 | 监听端口 |
| `JAVA_BASE_URL` | http://localhost:8081 | 校验 token 用的 Java 地址 |
| `RABBITMQ_URL` | amqp://guest:guest@localhost:5673/ | 本地是映射端口 5673，容器内是 5672 |
| `PUSH_QUEUE` | shiwei.queue.ws.push | 要消费的队列 |

Docker 里 `docker-compose up` 会自动带上，端口 8082。

## 连上来

浏览器的 WebSocket API 不能设自定义 header，所以 token 只能走 query：

```js
const token = localStorage.getItem('shiwei_token');
const ws = new WebSocket(`ws://localhost:8082/ws?token=${token}`);

ws.onmessage = (e) => {
  const { type, data, ts } = JSON.parse(e.data);
  if (type === 'SECKILL_RESULT' && data.status === 'SUCCESS') {
    location.href = `/order.html?id=${data.orderId}`;
  }
};

ws.onclose = () => setTimeout(connect, 1000 * Math.min(retry++, 10));
```

健康检查：`GET /healthz` 返回 `ok`
在线数：`GET /stats` 返回 `{"connections":3,"users":2}`

## 消息格式

Java 发到队列里：

```json
{ "userId": 4, "type": "SECKILL_RESULT", "data": { "status": "SUCCESS", "orderId": 12 } }
```

网关补个时间戳推给浏览器：

```json
{ "type": "SECKILL_RESULT", "data": { ... }, "ts": 1763452800000 }
```

`type` 目前有四个：`SECKILL_RESULT` / `ORDER_PAID` / `ORDER_VERIFIED` / `REFUND_CHANGED`。

## 代码结构

| 文件 | 干什么 |
|---|---|
| `main.go` | 路由、启动、优雅关闭 |
| `hub.go` | `userId → []*Client` 的注册表，`Push` 在这里 |
| `client.go` | 单个连接的读写协程、心跳、关闭 |
| `auth.go` | 调 Java 校验 token |
| `mq.go` | 订阅队列、解析、转发 |
| `config.go` | 环境变量与超时常量 |

## 六个刻意的写法

**1. 关闭用 `done` channel，不用 `close(send)`**

`close(send)` 之后再往里发会 panic。而 `Push` 可能正拿着这个连接的引用（已经从 hub 取出来了），窗口一定存在。

改成：`send` 永不关闭，关闭只靠 `close(done)`；`Push` 走 `select` 的非阻塞分支，满了就丢弃并踢连接。这样永远不会 panic。

**2. 读写必须两个 goroutine**

`gorilla/websocket` 不允许并发写。所有写操作（业务消息、ping、close）都集中在 `writePump` 里，其它地方只能往 `send` channel 塞。

**3. `Push` 里的锁只包一行**

```go
h.mu.RLock()
conns := h.clients[userID]   // 只有这一行需要锁
h.mu.RUnlock()
```

锁保护的是 map。拿到切片之后就不用锁了 —— 后面遍历的是切片副本，每个连接的操作互不影响。持锁时间越短，注册/注销越不会被堵。

**4. 先鉴权再 `Upgrade`**

`Upgrade` 一旦执行，HTTP 响应头已经发出去了，这时候再想返回 401 已经晚了。所以校验放在前面。

**5. `userId` 在 HTTP 里是字符串，在 MQ 里是数字**

Java 的 MVC ObjectMapper 配了 `ToStringSerializer`（JS 的 Number 存不下 64 位整数），所以 `/api/auth/me` 返回 `"id": "4"`。
而 MQ 用的是另一个独立 ObjectMapper，没这个配置，所以消息里是 `"userId": 4`。

Go 侧两处必须用不同类型接：`auth.go` 用 `string` 再 `ParseInt`，`mq.go` 用 `int64`。接错了就是静默解析失败。

**6. 队列用同步消费，不起 worker pool**

`Push` 是纯内存操作，写一个 channel 就返回了，不会成为瓶颈。上并发消费只会多出锁和顺序问题。

真到了消息堆积的规模再加 worker pool —— 但那时候瓶颈多半在别处。
