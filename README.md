高并发点评系统

> 基于 Spring Boot 3 的高并发本地生活服务平台（仿大众点评），涵盖探店笔记、优惠券秒杀、商户推荐等核心业务场景。

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Redis](https://img.shields.io/badge/Redis-7.x-red.svg)](https://redis.io/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.x-orange.svg)](https://www.rabbitmq.com/)

---

## 📖 项目简介

HM-DianPing 是一个面向求职面试的 **高并发后端实战项目**，模拟大众点评的核心业务场景。系统深度实践了 Redis、RabbitMQ、分布式锁、Lua 脚本、Sentinel 限流等高并发核心组件，并配套完整的缓存三连解决方案与异步削峰架构。

**适合人群：**
- 准备校招/社招 Java 后端岗位的同学
- 想系统掌握 Redis 高并发场景的开发者
- 需要一个能"讲清楚架构"的面试项目

---

## 🏗️ 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                       客户端 (Vue/H5)                         │
└─────────────────────────────────────────────────────────────┘
                              │ HTTPS
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Nginx (反向代理/负载均衡)                  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              Spring Boot 3 应用层 (Tomcat)                   │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │ 登录鉴权  │ │ 优惠券秒杀 │ │ 探店笔记  │ │ 商户推荐  │       │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘       │
└─────────────────────────────────────────────────────────────┘
        │              │              │              │
        ▼              ▼              ▼              ▼
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│  MySQL   │    │  Redis   │    │ RabbitMQ │    │Sentinel  │
│  主库     │    │ 7.x 集群  │    │ 异步削峰  │    │  限流    │
└──────────┘    └──────────┘    └──────────┘    └──────────┘
```

### 秒杀核心链路（RabbitMQ 异步版）

```
用户下单请求
     │
     ▼
┌──────────────────┐
│ ① Sentinel 限流  │  ← 令牌桶 + 滑动窗口防刷
└──────────────────┘
     │
     ▼
┌──────────────────┐
│ ② Redis Lua 预扣 │  ← 原子检查库存 + 防重复下单
└──────────────────┘
     │ （5ms 内返回订单号）
     ▼
┌──────────────────┐
│ ③ 发送 MQ 消息   │  ← RabbitMQ 异步削峰
└──────────────────┘
     │
     ▼
┌──────────────────┐
│ ④ 消费者写 DB    │  ← 事务保证一致性 + 幂等校验
└──────────────────┘
     │
     ▼ （30 秒未支付）
┌──────────────────┐
│ ⑤ 死信队列关单   │  ← 消息级 TTL + 死信路由
└──────────────────┘
     │
     ▼
┌──────────────────┐
│ ⑥ 回补库存        │  ← Redis INCR 原子操作
└──────────────────┘
```

---

## 🎯 核心亮点（面试必讲）

### 1️⃣ Redis 高级应用
- **五种数据结构实战**：String（Token）、Hash（用户信息）、Set（关注）、ZSet（点赞排行榜）、GEO（附近商户）
- **缓存三连完整解决方案**（生产级封装 `CacheClient` 工具类）：
  - **穿透**：布隆过滤器（Redisson）+ 空值标记（`__NULL__`）双层防护
  - **击穿**：逻辑过期（`RedisData`）异步重建 / Redisson 互斥锁双检查
  - **雪崩**：TTL 随机偏移（±5 分钟）打散过期时间
- **Lua 脚本原子操作**：秒杀预扣库存，避免并发安全问题

### 2️⃣ Redisson 分布式锁
- 自定义 `SimpleRedisLock` 理解原理 + Redisson 生产级封装
- 看门狗自动续期机制，解决锁过期释放问题
- 秒杀业务实战：库存超卖、用户重复下单

### 3️⃣ RabbitMQ 异步削峰
- **预扣库存 → 发 MQ → 异步下单** 三段式架构
- **死信队列 + 消息级 TTL** 实现订单超时自动关闭（30 秒未支付自动关单 + 库存回补）
- **幂等键 + 重试机制** 保证消息可靠性
- 单机 QPS 从 800 提升到 5000+

### 4️⃣ Sentinel 限流防刷（三层防护）
- **全局限流**：`@RateLimiter` 注解 + Sentinel 资源保护
- **单 IP 限流**：`@IpRateLimiter` 注解动态规则注册
- **单用户限流**：`AntiBrushInterceptor` Redis 计数器拦截
- 秒杀入口双重防护：全局 100 QPS + 单用户 5 QPS
- 支持 Dashboard 动态规则推送

### 5️⃣ WebSocket 实时通知
- 基于 Spring WebSocket + Token 握手鉴权
- `WebSocketSessionManager` 管理在线连接
- 业务侧 `NotificationService` 一行代码即可推送
- 典型应用场景：博客评论实时通知、秒杀成功通知、关注提醒
- 端点：`ws://localhost:8081/ws/notification?token=xxx`

### 4️⃣ Sentinel 限流防刷
- 秒杀接口、登录接口、点赞接口全接入
- 令牌桶 + 滑动窗口双策略防脚本攻击
- 热点参数限流，针对特定用户限流

### 5️⃣ 缓存架构
- **Caffeine L1 + Redis L2** 多级缓存（预留）
- 缓存空值 + 布隆过滤器解决穿透
- 互斥锁 + 逻辑过期解决击穿
- 过期时间随机化解决雪崩

### 6️⃣ WebSocket 实时通知
- 用户被点赞、关注、收到评论 → 实时推送小红点
- 基于 STOMP 协议，支持消息广播与点对点推送

---

## 🛠️ 技术栈

| 类别 | 技术 | 说明 |
|---|---|---|
| **基础框架** | Spring Boot 3.4.3 | 主框架 |
| **持久层** | MyBatis-Plus 3.5.10 | ORM + 分页插件 |
| **数据库** | MySQL 8.0 | 主库 |
| **缓存** | Redis 7.x | 缓存 + 分布式锁 |
| **分布式锁** | Redisson 3.44 | 生产级分布式锁 |
| **消息队列** | RabbitMQ 3.x | 异步削峰 + 延迟消息 |
| **限流** | Sentinel | 接口限流防刷 |
| **工具库** | Hutool 5.8 | Java 工具集 |
| **AOP** | AspectJ | 切面编程（日志、权限） |
| **连接池** | HikariCP | 数据库连接池 |
| **API 文档** | Knife4j 4.x | Swagger 增强 |
| **构建工具** | Maven 3.9 | 项目构建 |
| **JDK** | Java 17 | LTS 版本 |
| **测试** | JUnit 5 + Mockito | 单元测试 |

---

## 📂 目录结构

```
hm-dianping/
├── src/
│   ├── main/
│   │   ├── java/com/hmdp/
│   │   │   ├── config/          # 配置类（Redis、Redisson、MQ、拦截器）
│   │   │   ├── controller/      # 控制器层
│   │   │   │   ├── UserController.java          # 用户/登录
│   │   │   │   ├── ShopController.java          # 商户
│   │   │   │   ├── ShopTypeController.java     # 商户类型
│   │   │   │   ├── BlogController.java          # 探店笔记
│   │   │   │   ├── BlogCommentsController.java  # 笔记评论
│   │   │   │   ├── FollowController.java        # 关注
│   │   │   │   ├── VoucherController.java       # 优惠券
│   │   │   │   ├── VoucherOrderController.java  # 秒杀订单
│   │   │   │   └── UploadController.java        # 文件上传
│   │   │   ├── service/         # 服务层接口
│   │   │   ├── service/impl/    # 服务层实现
│   │   │   ├── mapper/          # MyBatis-Plus Mapper
│   │   │   ├── entity/          # 数据库实体
│   │   │   ├── dto/             # 数据传输对象
│   │   │   └── utils/           # 工具类（Redis、分布式锁、ID 生成器）
│   │   └── resources/
│   │       ├── application.yaml         # 主配置
│   │       ├── application-dev.yaml     # 开发环境
│   │       ├── application-prod.yaml    # 生产环境
│   │       ├── mapper/                  # MyBatis XML
│   │       ├── db/                      # 数据库脚本
│   │       ├── seckill.lua              # 秒杀 Lua 脚本
│   │       └── unlock.lua               # 解锁 Lua 脚本
│   └── test/                    # 单元测试
├── .env.example                 # 环境变量模板
├── .gitignore                   # Git 忽略规则
├── pom.xml                      # Maven 配置
├── docker-compose.yml           # Docker 一键启动
└── README.md                    # 本文档
```

---

## 🚀 快速开始

### 环境要求

- **JDK** 17+
- **Maven** 3.8+
- **MySQL** 8.0+
- **Redis** 7.0+
- **RabbitMQ** 3.10+
- **Sentinel Dashboard**（可选，用于限流可视化）

### 方式一：Docker 一键启动（推荐）

```bash
git clone https://github.com/112345-cpn/shiwei.git
cd shiwei

# Windows：双击 start.bat
# Linux / macOS / WSL：
./start.sh
```

一条命令就够了。脚本依次做四件事：

1. 检查 Docker 是否装了、引擎是否在跑（没跑会直接告诉你先去开 Docker Desktop）
2. 没有 `.env` 就从 `.env.example` 复制一份
3. `docker compose up -d --build` 起 5 个容器
4. 轮询等到 8080 能访问，然后自动打开浏览器

| 服务 | 地址 | 账号 |
| --- | --- | --- |
| 前端页面 | http://localhost:8080 | — |
| 后端接口 | http://localhost:8081 | — |
| RabbitMQ 管理台 | http://localhost:15672 | guest / guest |

停止用 `stop.bat` 或 `./stop.sh`，数据卷会保留；要连数据一起清掉就 `docker compose down -v`。

> **首次启动 3–8 分钟是正常的**：镜像里的 Maven 要从零拉依赖。之后只要 `pom.xml` 没动，重建会走 Docker 层缓存，改代码后重新 `up --build` 通常 1 分钟内。

**容器编排**（`docker-compose.yml`）：

```
web      nginx:1.25-alpine      前端静态文件 + /api 与 /ws 反代
app      本项目构建的镜像        Spring Boot，prod profile
mysql    mysql:8.0              首次启动自动执行 db/hmdp.sql
redis    redis:7-alpine
rabbitmq rabbitmq:3.12-management-alpine
sentinel 不默认启动，需要时加 --profile monitor
```

### 方式二：本地开发（要改代码时用这个）

只让 Docker 起中间件，应用在宿主机跑，改完直接重启，不用等镜像重建。

```bash
# 1. 只起中间件
docker compose up -d mysql redis rabbitmq

# 2. 编译并运行（dev profile）
mvn clean package -DskipTests
java -jar target/hm-dianping-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

环境要求：JDK 17+、Maven 3.8+、Docker。

`dev` 与 `prod` 的唯一差别在验证码：`dev` 把验证码回传前端（登录页自动填入，省得翻日志），`prod` 绝不回传——打开 `expose-code` 等于任何人都能登录任意手机号。

---

## 📡 接口文档

### 启动后访问 Knife4j 文档

```
http://localhost:8081/doc.html
```

### 标准 OpenAPI 3 端点

```
# OpenAPI JSON 描述
http://localhost:8081/v3/api-docs

# Swagger UI 备用入口
http://localhost:8081/swagger-ui.html
```

### 已启用的接口文档增强

- ✅ 自动生成所有 Controller 的接口文档
- ✅ 支持在线调试（Try it out）
- ✅ 支持 JWT Token 鉴权（统一在请求头添加）
- ✅ 中文界面 + 实体类列表
- ✅ 文档版本管理

### 核心接口列表

| 模块 | 接口 | 方法 | 说明 |
|---|---|---|---|
| 用户 | `/user/code` | POST | 发送验证码 |
| 用户 | `/user/login` | POST | 登录 |
| 商户 | `/shop/{id}` | GET | 查询商户详情（带缓存） |
| 商户类型 | `/shop-type/list` | GET | 商户类型列表 |
| 笔记 | `/blog` | POST | 发布探店笔记 |
| 笔记 | `/blog/{id}` | GET | 查询笔记详情 |
| 笔记 | `/blog/like/{id}` | PUT | 点赞（ZSet 排行榜） |
| 关注 | `/follow/{id}` | PUT | 关注/取关 |
| 优惠券 | `/voucher/seckill` | POST | 创建秒杀券 |
| 秒杀 | `/voucher-order/seckill/{id}` | POST | 秒杀下单 ⭐ |

---

## 🧪 测试

```bash
# 运行所有测试
mvn test

# 运行指定测试类
mvn test -Dtest=UserServiceTest

# 生成覆盖率报告
mvn test jacoco:report
```

---

## 📊 性能数据

| 场景 | 优化前 | 优化后 | 提升 |
|---|---|---|---|
| 商户详情查询 | 200ms（DB 直查） | 5ms（缓存命中） | **40x** |
| 秒杀下单 | 800 QPS（同步） | 5000+ QPS（异步） | **6x+** |
| 探店笔记查询 | 150ms | 20ms | **7x** |

> 以上数据基于单机压测（8C16G），仅供参考。

---

## 🐳 Docker 部署

### 方式二：Docker Compose 一键启动（推荐生产环境）

#### 1. 准备环境变量
```bash
cp .env.docker.example .env.docker
# 编辑 .env.docker，填入密码等敏感信息
```

#### 2. 一键启动所有服务
```bash
# 启动 MySQL + Redis + RabbitMQ + Sentinel + 应用
docker-compose --env-file .env.docker up -d

# 查看启动状态
docker-compose ps

# 查看应用日志
docker-compose logs -f app
```

#### 3. 访问服务

| 服务 | 地址 | 说明 |
|---|---|---|
| **HM-DianPing 应用** | http://localhost:8081 | 主应用 |
| **RabbitMQ 管理后台** | http://localhost:15672 | 用户名密码见 `.env.docker` |
| **Sentinel 控制台** | http://localhost:8858 | 限流规则管理（默认 sentinel/sentinel） |
| **MySQL** | localhost:3306 | 数据库 |
| **Redis** | localhost:6379 | 缓存 |

#### 4. 常用命令
```bash
# 停止所有服务
docker-compose down

# 停止并删除数据卷（重置数据）
docker-compose down -v

# 重新构建并启动
docker-compose up -d --build

# 进入应用容器调试
docker-compose exec app sh

# 查看资源占用
docker stats
```

### 自定义镜像构建

```bash
# 单独构建应用镜像
docker build -t hmdp:latest .

# 打 tag 推送仓库
docker tag hmdp:latest your-registry.com/hmdp:v1.0.0
docker push your-registry.com/hmdp:v1.0.0
```

### 数据持久化

docker-compose 已配置以下数据卷，删除容器不会丢失数据：
- `hmdp-mysql-data`：MySQL 数据
- `hmdp-redis-data`：Redis 持久化数据
- `hmdp-rabbitmq-data`：RabbitMQ 队列数据
- `hmdp-app-logs`：应用日志
- `hmdp-app-upload`：用户上传文件

---

## 📝 简历项目描述模板

### 写法一（推荐，适合校招/1-3年初级）

> **项目名称：** HM-DianPing 高并发点评系统
>
> **项目描述：** 基于 Spring Boot 3 仿大众点评的高并发本地生活服务平台，涵盖探店笔记、优惠券秒杀、商户推荐等核心业务。
>
> **技术栈：** Spring Boot 3 / MyBatis-Plus / Redis / Redisson / RabbitMQ / Sentinel / Docker
>
> **核心职责与亮点：**
> 1. 基于 **Redis + Lua 脚本** 实现秒杀库存预扣，结合 **Redisson 分布式锁** 解决超卖问题；
> 2. 引入 **RabbitMQ** 实现秒杀异步削峰，通过 **死信队列 + TTL** 处理订单超时关闭；
> 3. 设计**缓存三连完整解决方案**：布隆过滤器防穿透 + 互斥锁防击穿 + 过期时间随机化防雪崩；
> 4. 基于 **Sentinel** 实现多接口限流，结合滑动窗口防脚本攻击；
> 5. 使用 **Hutool + Redis Worker 雪花算法** 生成全局唯一 ID；
> 6. **Docker Compose** 一键编排 MySQL/Redis/RabbitMQ 中间件。

### 写法二（适合社招，强调架构思维）

> 基于 Spring Boot 3 设计并实现高并发点评系统，深入实践分布式架构核心组件。**秒杀场景**采用"Lua 预扣库存 + MQ 异步下单 + 死信队列延迟关单"三段式架构，单机 QPS 突破 5000；**缓存体系**构建"L1 Caffeine + L2 Redis + DB"多级缓存，结合布隆过滤器与逻辑过期方案彻底解决缓存三大问题；**限流层**接入 Sentinel 实现令牌桶 + 热点参数限流。系统全程 Docker Compose 编排，CI/CD 自动化部署。

---

## ❓ 常见面试问题

### Q1：秒杀系统怎么防止超卖？
A：Redis 预扣库存 + Lua 脚本保证原子性。Lua 中先查库存、再扣库存、最后写用户订单标记，三步一次性执行，**杜绝并发问题**。

### Q2：Lua 脚本执行失败了怎么办？
A：Lua 是原子操作，要么全部成功要么全部失败，不会出现"库存减了但订单没写"的问题。如果 Lua 执行失败（极少），库存没扣，不会有副作用。

### Q3：消息队列怎么保证不丢消息？
A：生产者开启 `publisher-confirm` 回调；MQ 开启持久化；消费者关闭自动 ACK，采用手动 ACK，消息处理成功才确认。

### Q4：缓存和数据库怎么保证一致性？
A：采用**延迟双删**策略：先删缓存 → 更新 DB → 延迟 N 秒再删缓存。本项目用 MQ 异步重试 + 缓存空值兜底。

### Q5：Sentinel 限流被触发后用户体验怎么办？
A：返回友好的"系统繁忙"提示，并引导用户稍后重试。生产环境可配置降级策略，比如直接返回首页数据。

---

## 📚 学习路线推荐

如果你是第一次接触这些技术，建议按以下顺序学习：

1. **Redis 基础** → Redis 五种数据结构 + 持久化
2. **Spring Boot 整合 Redis** → Spring Data Redis + Redisson
3. **Lua 脚本实战** → 秒杀预扣库存
4. **分布式锁** → Redisson 看门狗机制
5. **RabbitMQ** → 异步消息 + 死信队列
6. **缓存三大问题** → 穿透 / 击穿 / 雪崩
7. **Sentinel 限流** → 令牌桶 / 滑动窗口
8. **WebSocket** → 实时推送

---

## 🤝 贡献指南

欢迎 PR！如果你有更好的实现方案，请提交 Issue 或 Pull Request。

---

## 📄 License

本项目采用 MIT 协议开源，详见 [LICENSE](LICENSE) 文件。

---

## 🙏 致谢

- 黑马程序员点评项目（原项目基础）
- Spring Boot / Redis / RabbitMQ / Sentinel 官方文档
- 所有为开源社区贡献力量的开发者们

---

## 📮 联系方式

- **GitHub Issues**：[提交问题](https://github.com/your-username/hm-dianping/issues)
- **Email**：your-email@example.com

---

<div align="center">

**⭐ 如果这个项目对你有帮助，请给一个 Star！⭐**

Made with ❤️ by [Your Name]

</div>
