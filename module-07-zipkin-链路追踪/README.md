# 模块 07：Micrometer Tracing + Zipkin 链路追踪 —— 看见请求的完整一生

> 目标：下一次"走网关下单"，到 Zipkin UI 里看这条请求的**完整轨迹**：
> gateway → order-service →（Feign）→ user-service → MySQL，每一跳耗时多少、谁最慢，一图看穿。

---

## ① 项目背景：微服务的"盲飞"困境

模块 05 里你有没有发现一件事：走网关报 500/超时时，**你不知道时间花在了哪一跳**——
是网关慢？order-service 慢？还是 user-service 的 SQL 慢？日志散落在 3 个进程里，
靠肉眼翻日志拼出完整链路几乎不可能。

**分布式链路追踪**给每个请求发一张"身份证"（TraceId），每经过一站就记一段行程（Span），
最后汇总到 Zipkin 画出**瀑布图**。以后排障就是：拿 TraceId → 搜 Zipkin → 哪跳慢、哪跳错，一眼定位。

## ② 概念文档：Trace / Span / 上下文传播

### 2.1 三个核心名词

| 概念 | 类比 | 例子（一次走网关的下单） |
|------|------|------------------------|
| **Trace** | 一张完整的行程单 | 从"用户点下单"到"响应返回"的全过程，一个全局唯一 TraceId |
| **Span** | 行程单上的一行（一段耗时区间） | `GET /api/orders`、`order-service 的 createOrder`、`Feign GET /users/1`、`SQL SELECT t_user`——每个都是 Span，有开始/结束/耗时 |
| **Span 父子关系** | 谁包含了谁 | gateway 的 Span 是父，order-service 的 Span 是子，Feign 调用 Span 是孙——组成一棵树 |

### 2.2 跨进程怎么串起来？—— 上下文传播

order-service 怎么知道"我和网关处理的是同一个请求"？靠 **HTTP Header**：

```
网关处理请求时生成 TraceId
   → 转发时在请求头带上：traceparent: 00-{traceId}-{spanId}-01
   → order-service 收到请求，读 Header，知道"我是这个 Trace 的子环节"
   → order-service 用 Feign 调 user-service 时，又把 Header 原样带上
   → 每个服务本地记录 Span，异步批量上报给 Zipkin
   → Zipkin 按 TraceId 把散落的 Span 拼成一棵树
```

这就是为什么**追踪对业务代码零侵入**：上下文传播由 HTTP 客户端/服务端框架自动完成
（Spring MVC、WebFlux、Feign、JDBC 都内置了埋点），我们只加依赖+一行配置。

### 2.3 架构图

```
┌─gateway:8080─┐   header带traceid   ┌─order:8082─┐  Feign带traceid  ┌─user:8081─┐
│  记录 Span A │ ──────────────────► │ 记录 Span B │ ──────────────► │ 记录Span C │
└──────┬───────┘                     └──────┬──────┘                  └─────┬──────┘
       │ 异步批量上报（不阻塞业务请求）          │                              │
       ▼                                    ▼                              ▼
      ┌─────────────────── Zipkin :9411 ────────────────────┐
      │  收集所有 Span → 按 TraceId 拼树 → UI 画瀑布图        │
      └──────────────────────────────────────────────────────┘
```

### 2.4 技术选型说明（为什么是 Micrometer Tracing）

Spring Boot 3 时代官方钦定的追踪门面是 **Micrometer Tracing**（取代 Boot 2 时代的 Sleuth），
底层可以配 **Brave**（我们用的桥）或 OpenTelemetry；上报给 **Zipkin**（轻量、UI 友好，学习首选）
或 Jaeger/SkyWalking（生产重器，业界也很流行，思路完全一致）。

### 2.5 在微服务版图中的位置

追踪是**观测层**：不改业务行为，只记录。它和 Sentinel（保护层）互补——Sentinel 拦住坏请求，
Zipkin 告诉你坏请求去过哪、死在哪。模块 08/09 的异步消息、分布式事务同样会被自动追踪。

## ③ 链路分析：一次下单在 Zipkin 里的样子

请求 `POST http://localhost:8080/api/orders` 后，Zipkin 中应出现这样的瀑布树：

```
TraceId: 6a7f8e...（一次下单）
└─ POST /api/orders            [gateway]      ████ 12ms
   └─ POST /orders             [order]        ███ 10ms
      ├─ createOrder           [order]        ███ 9ms   ← @SentinelResource 也是埋点！
      │  └─ GET /users/1       [order→user]   ██ 4ms    ← Feign 调用（HTTP 客户端自动埋点）
      │     └─ GET /users/{id} [user]         ██ 3ms
      │        └─ SELECT       [user]         █ 1ms     ← JDBC 自动埋点
      └─ INSERT t_order        [order]        █ 1ms
```

**容易出错的地方**：
1. **链路断开**（Zipkin 里只见 gateway 不见下游）：中间某服务的 Header 没传下去——标准组件都会传，若自定义 RestTemplate 手动发请求要自己加拦截器；
2. **看不到数据**：上报是**异步批量**的（默认攒一批或按间隔），请求完等 2~5 秒再搜；
3. 生产全采样会把 Zipkin 撑爆：`sampling.probability` 调到 0.1，只抽 10%。

## ④ 常见问题与解决思路

### Q1：Zipkin UI 里搜不到任何 Trace
排查三连：① `docker ps` 看 micro-zipkin 起没起；② 三个服务重启了吗（新依赖+新配置要生效）；③ 采样率是不是 0（`management.tracing.sampling.probability: 1.0`）；④ 请求完**等几秒**再搜（异步上报有延迟），时间范围选 15 分钟/1 小时。

### Q2：Trace 只有一段（比如只有 order-service，没有 gateway 和 user）
说明调用链在某处断了：① 请求是不是直连的 8082（绕过网关当然没有 gateway Span——这不算问题）；② Feign 调用的 Header 传播被自定义配置破坏；③ 网关的追踪依赖没加。

### Q3：`http://localhost:9411/api/v2/spans` 404 / 连不上
endpoint 路径必须是 `/api/v2/spans`（v2 API）；容器没起或端口没映射 → `docker logs micro-zipkin`。

### Q4：每个服务日志里的 TraceId 有什么用？
日志报错时把 TraceId 打出来（Micrometer 自动往 MDC 里塞了 traceId，日志格式里会出现一串 hex）。**跨服务排障的钥匙**：拿这个 ID 去 Zipkin 一搜，整条链路的生死时速全出来。

### Q5：想要 SkyWalking 怎么办？
思路一致（Agent 探针方式部署更重、UI 更强、带拓扑图）。学习期用 Zipkin 打好概念底子，SkyWalking 上手半天即可——**概念比工具值钱**。

## ⑤ 动手实践（15 分钟）

### 第 1 步：启动 Zipkin
```bash
docker compose -f docker/docker-compose.yml up -d zipkin
```
浏览器 http://localhost:9411 能看到 UI。

### 第 2 步：重启三个服务
IDEA：Maven 刷新 → 重启 UserApplication、OrderApplication、**GatewayApplication**（三个都要！trace 才完整）。

### 第 3 步：下一次"会师"请求
```bash
printf '{"userId":1,"productName":"链路追踪测试","amount":66.60}' > /tmp/order.json
curl -X POST http://localhost:8080/api/orders -H "Content-Type: application/json; charset=utf-8" --data-binary @/tmp/order.json
```

### 第 4 步：Zipkin 里找到它（高潮）
1. 等 3~5 秒 → http://localhost:9411 → 点 **Run Query**；
2. 点最新一条 Trace → 看瀑布图：**gateway → order → user 的三级 Span 树**，每段耗时、每个服务名一目了然；
3. 点开 Feign 那个 Span 还能看到 HTTP 详情。
**截图这张瀑布图发我**（这就是你微服务之旅的"全家福"）。

### 第 5 步：提交（导师已完成，供对照）
```bash
git commit -m "feat(module-07): 集成 Micrometer Tracing + Zipkin 全链路追踪"
```
改动：docker-compose 加 zipkin、三个服务加 actuator+tracing 依赖、三个 yml 加采样与上报地址。

## ⑥ 学习检查

**第 1 题（简答）**：Trace、Span 的关系是什么？"一次走网关的下单"大概会产生哪几个 Span？

**第 2 题（简答）**：order-service 是怎么知道"自己和网关在处理同一个请求"的？这套机制叫什么、靠什么载体实现？

**第 3 题（简答）**：生产环境为什么把 `sampling.probability` 从 1.0 调到 0.1？如果只想追踪"所有失败的请求"，你有什么思路？
