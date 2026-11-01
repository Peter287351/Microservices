# STAGE 5 · 响应式网关与全链路追踪

> 旧实现（参考答案）：module-05（Gateway 路由+JWT 验签）、module-07（Zipkin 三级瀑布）。
> 本关两座新山：① 理解 **为什么网关里一行同步阻塞代码=事故** ② 亲手写 **GlobalFilter** 并打通 **TraceId 跨进程+跨线程池** 的完整闭环。

---

## 1. 源码与八股深度剖析

### 1.1 Netty Reactor 响应式非阻塞模型（为什么严禁同步阻塞）

```
Servlet/Tomcat（阻塞）              WebFlux/Netty（非阻塞）
请求 ──► 线程(1:1 独占到响应结束)    请求 ──► EventLoop（少量线程轮转海量连接）
200 并发 = 200 线程                 200 并发 = 8 个 EventLoop 轮转
阻塞 3s = 线程干等 3s               阻塞 3s = 这条 EventLoop 上【所有连接】卡 3s
```
**红线**：Gateway 的任何 Filter/Handler 里做同步阻塞调用（JDBC、`restTemplate.getForObject`、`Thread.sleep`），
等于掐住整个 EventLoop——并发越高死得越快，且错误不在业务代码而在"污染了公共车道"。
正确姿势：需要同步逻辑时 `publishOn(Schedulers.boundedElastic)`（弹性线程池，专门承接阻塞段），或改用异步客户端（WebClient/R2DBC）。

### 1.2 Route / Predicate / Filter 工作流（源码视角）

```
请求 → HttpWebHandlerAdapter → DispatcherServlet(响应式版=WebHandler)
 → RoutePredicateHandlerMapping：遍历路由集合，逐条跑 Predicate（Path/Header/Method/Time…）
 → 命中 → 组装 GatewayWebFilterChain
     = 全局 GlobalFilter + 该路由的 GatewayFilter 合并，按 Order 排序
 → 依次执行（pre 段）→ NettyRoutingFilter 发起真实转发 →（post 段逆序回程）
```
你已用过的组件定位：`StripPrefix`（GatewayFilter）、JWT 验签（SecurityWebFilterChain，在 Filter 链更外层）、
本关要写的 GlobalFilter = 插进这条链的自定义环节（比如：把 JWT 里的 userId 染到请求头传给下游）。

### 1.3 TraceId 完整闭环（跨进程 + 跨线程）

```
① 网关入口：brave/TracingFilter 自动提取或生成 TraceId（b3/traceparent Header）
② 跨进程：Feign(micrometer 桥)/RestTemplate 拦截器把 Header 带给下游（E07-1 教训：Feign 要单独装配）
③ 服务端：MDC.put(traceId) → 日志每行自动携带（你日志里 [6a9471e5-...] 那串）
④ 跨线程：⚠️ 旧工程没做过——@Async/自建线程池/new Thread 里 MDC 是【空的】
     解决：TaskDecorator（提交任务时快照父线程 MDC，执行前复制）或 TransmittableThreadLocal(TTL)
     Reactor 侧：Micrometer context-propagation 自动接力（Spring Boot 3 已内置大部分场景）
```
闭环的意义：用户投诉"这一单错了" → 拿 TraceId → **一次搜索看到网关/order/user/SQL 全部生死时速**。

### 1.4 响应式里的上下文陷阱（面试区分度最高的点）

- MVC：ThreadLocal 天然可用（一请求一线程）
- WebFlux：一个请求可能横跨多个 EventLoop 线程，**ThreadLocal 不可靠** → 用 Reactor `Context`
- Micrometer 的 context-propagation 桥接了两者；但**自建线程池必须显式装饰**——这就是本关 TaskDecorator 任务的由来

---

## 2. 实战任务清单（代码锚点：`// TODO [Stage-5]`）

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 1 | **自定义 GlobalFilter** | `gateway-service/.../filter/TraceIdGlobalFilter.java`（骨架已建） | `TODO [Stage-5]`：实现 `GlobalFilter, Ordered`；从 SecurityContext/JWT 取 userId，染色 `X-User-Id` 头传给下游；Order 值写多少并说明理由（要在转发前） |
| 2 | 下游感知染色 | `order-service` 加一个接口或日志 | 验证下游收到 `X-User-Id`（证明 GlobalFilter 生效） |
| 3 | **跨线程 TraceId** | `order-service/.../config/AsyncConfig.java`（骨架已建） | `TODO [Stage-5]`：自建 ThreadPoolTaskExecutor + TaskDecorator 复制 MDC；在 service 里 `@Async` 打一条日志，证明 traceId 不丢 |
| 4 | 阻塞实验对比 | 临时代码 | 在 Gateway Filter 里写一行 `Thread.sleep(500)` → 20 并发压测 → 全站卡死实锤；改成 `boundedElastic` 包装 → 恢复。**做完必须删** |
| 5 | （选做）TraceId 日志门面 | `logging.pattern` | 把 traceId 显式加进日志 pattern（现在日志里已有，理解它从哪来） |

## 3. 改造实验（Predict → Run → Verify）

1. **阻塞毁网关实验**（本关最震撼）：任务 4 的前后对比——预测：阻塞 500ms × 20 并发，网关吞吐掉到多少？实际：注意**不经过该路由的其他请求是否也卡**（EventLoop 共享的体感）。
2. **染色追踪实验**：带 JWT 下单 → order 日志里出现 `X-User-Id: 1` → 拿这条日志的 TraceId 去 Zipkin 搜索 → 同一 ID 下能看到 gateway/order/user 三段 Span（**跨进程闭环**）。
3. **跨线程实验**：改造前后各跑一次 `@Async` 日志——改造前 traceId 是空的（或错乱的），改造后与主线程一致。亲眼看见"上下文会断"。
4. **半开回调实验（联动 Stage 4）**：给网关转发路径配熔断，观察 Open→Half-Open 时 Zipkin 里探测请求的独特轨迹。

## 4. 本关必考深水区面试题（@面试 5 拷问）

1. **"网关代码里一行 `Thread.sleep` 为什么是事故级写法？从 Netty EventLoop 模型推演雪崩过程，并给出至少两种整改方案。"**
2. **"TraceId 从浏览器到 MySQL 全程不丢，涉及哪几段传播？跨进程和跨线程分别靠什么机制？MDC 的底层数据结构是什么、为什么它不能跨线程池？"**
3. **"GlobalFilter 的 order 值决定执行顺序，你的鉴权染色过滤器应该排在路由转发之前还是之后？如果排在 JWT 验签（SecurityWebFilterChain）之前会发生什么？"**
