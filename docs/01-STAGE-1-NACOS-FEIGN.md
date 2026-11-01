# STAGE 1 · 服务治理与通信基石（Nacos 深水区 / Feign 源码链路 / 超时重试边界）

> 旧实现（参考答案）：module-01/03/04。本关不做重复建设，聚焦三件旧工程**从没做过**的事：
> ① 从源码视角重走一遍已跑通的路 ② 新服务 inventory-service 从零接入 ③ 给 order-service 补上**从未配置过**的 Feign 超时。

---

## 1. 源码与八股深度剖析

### 1.1 Nacos 2.x gRPC 长连接与服务剔除

1.x 时代：客户端每 5s 发 HTTP 心跳，服务端被动计时（15s 不健康 / 30s 剔除）——心跳风暴 + 感知延迟。
2.x 时代：客户端启动即与 `服务端端口+1000`（我们映射的 9848）建立 **gRPC 双向流长连接**：

- 注册/注销/订阅 全走这条连接（回看 E08 之前的日志：`Grpc connection connect`、`NotifySubscriberRequest`）
- **连接即心跳**：gRPC 连接活着=实例活着，服务端无需逐实例心跳；连接断开触发主动剔除（比 1.x 快）
- **推送代替拉取**：实例变更由服务端经连接主动推（`NotifySubscriberRequest`），客户端缓存+redo 重放兜底
- 你在模块 01 实验里亲眼见过全套：`Fail to connect`（断连重试）→ `mark to redo` → `Redo instance operation REGISTER`（重连后重放注册）

**剔除时间线（临时实例）**：连接断开 → 15s 标记不健康 → 30s 删除；期间订阅方收到推送更新本地缓存。

### 1.2 AP（Distro）与 CP（Raft）

| | 临时实例（ephemeral=true，默认） | 持久实例（ephemeral=false） |
|---|---|---|
| 协议 | **AP · Distro**（Nacos 自研） | **CP · JRaft** |
| 存储 | 内存 + 集群异步复制 | 磁盘持久化 + 多数派写入 |
| 健康检查 | 客户端连接/心跳，超时自动剔除 | 服务端主动探测，不健康只标记不删除 |
| 场景 | 微服务实例（起伏频繁，可用性优先） | 数据库/中间件代理（配置驱动，一致性优先） |

选择本质：**分区发生时，拒绝服务（CP）还是返回可能过期的名单（AP）**。微服务调用方本就有重试+熔断兜底，所以临时实例默认走 AP。配置中心是另一码事——配置错误影响所有实例，一致性优先。

### 1.3 OpenFeign 源码链路（从注解到 HTTP）

```
@EnableFeignClients
  └─ FeignClientsRegistrar（ImportBeanDefinitionRegistrar）
       ├─ 扫描 @FeignClient 接口 → 注册 FeignClientFactoryBean（BeanDefinition）
       └─ (可选) 注册全局默认配置
容器获取 UserClient 时
  └─ FeignClientFactoryBean.getObject()
       ├─ FeignContext（NamedContextFactory）为每个 client 建独立子容器
       ├─ 组装：Contract(解析注解) / Encoder / Decoder / Retryer / Client
       └─ Feign.builder().target(...) → ReflectiveFeign 生成 JDK 动态代理
方法调用
  └─ 代理 → SynchronousMethodHandler（每方法一个）
       ├─ 按注解生成 RequestTemplate（URL/头/体）
       ├─ 执行 Retryer（Spring Cloud 默认 NEVER_RETRY）
       └─ client.execute() → LoadBalancerFeignClient
            └─ 以服务名取实例（LoadBalancer）→ 替换 URL → 真实 HTTP
```

你亲手抓过的证据：模块 03 报错栈里的 `$Proxy134` 与 `FeignInvocationHandler.invoke`；
模块 07 的教训：**Feign 埋点（feign-micrometer）不在默认链路里，要单独装配**。

### 1.4 Spring Cloud LoadBalancer 本地负载均衡

- 调用方进程内持有实例缓存：`ServiceInstanceListSupplier`（discovery → 缓存 caffeine）→ `ReactorServiceInstanceLoadBalancer`
- `RoundRobinLoadBalancer`：AtomicInteger 位置指针 `+1 mod 实例数`——无共享状态，天然服务实例级独立
- 触发时机：Feign 的 `LoadBalancerFeignClient` / Gateway 的 `lb://` 都汇到这一个抽象（模块 04 你看过 8081/8083 交替）

### 1.5 ConnectTimeout 与 ReadTimeout

| | ConnectTimeout | ReadTimeout |
|---|----------------|-------------|
| 覆盖阶段 | TCP 三次握手完成 | 连接已建立，**等待响应数据**的最大间隔 |
| 超时含义 | 目标不可达/握手慢（请求**确定未达**） | 请求已达，处理中或卡死（**结果未知**） |
| 安全重试？ | ✅ 大体安全（连接都没建立） | ⚠️ 危险——服务端可能已扣款已落库，重试=重复动作 |

**耗时预算公式**（面试常考）：
```
单次调用最坏耗时 = ConnectTimeout + ReadTimeout
开启重试（R 次）后最坏 = (C + R_d) × (R+1)      ← 乐观：每次都超时
调用链 N 层串行时按层相乘放大，超时必须逐层递减（下游 < 上游）
```
Spring Cloud OpenFeign 配置键（**旧工程从未配置过，默认 connect 10s / read 60s**）：
```yaml
spring.cloud.openfeign.client.config.<服务名>:
  connect-timeout: 2000     # ms
  read-timeout: 5000
```
默认 `Retryer = NEVER_RETRY`——Spring Cloud 团队替你避免了裸重试资损（Stage 2 你会亲手造一次资损再亲手堵上）。

---

## 2. 实战任务清单（代码锚点：`// TODO [Stage-1]`）

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 1 | 新服务接入注册+被调用 | `inventory-service/**`（新模块，MyBatis-Plus） | pom 依赖、yml 注册、启动类、Controller/Service/Mapper 骨架已建好；TODO 处补齐注册所需配置与一个库存查询接口实现 |
| 2 | Feign 超时配置 | `order-service/src/main/resources/application.yml` → `[Stage-1]` 区块 | 给 user-service、inventory-service 分别配 connect/read 超时（键位提示已写，数值你来定并说明理由） |
| 3 | order→inventory 的 Feign 客户端 | `order-service/.../client/InventoryClient.java` | 接口已声明，方法签名 `deductStock` 的注解部分 TODO |
| 4 | （可选手敲）盲写挑战 | 随时 | 合上资料默写 UserClient 接口 + 超时配置块，对照 git 历史 |

## 3. 改造实验（Predict → Run → Verify，烙印核心）

1. **超时感知实验**：给 inventory 查询接口加 `Thread.sleep(8000)`。预测：order 侧多久报错？报什么异常？改 read-timeout=3000 再跑。验证后**删掉 sleep**。
2. **AP 感知实验**：杀掉 inventory，立即狂刷 order 的调用 → 30s 内观察 LB 报错与 Nacos 控制台状态变化时间线，对照 1.1 的剔除时序。
3. **重试边界实验**：把 read-timeout 改成 200ms 且 inventory 处理真实耗时 500ms（模拟"已达但慢"），观察是否出现**同一请求被处理多次的迹象**（日志计数）——为 Stage 2 埋下动机。

## 4. 本关必考深水区面试题（不写答案，@面试 1 时拷问）

1. **Nacos 2.x 为什么把心跳改成了 gRPC 长连接？连接断开和心跳超时两种情况下，剔除策略有什么区别？**
   （考察：对 1.x→2.x 演进动机的理解、连接级健康 vs 应用级心跳）
2. **临时实例和持久实例背后是两套协议，请从"分区发生时客户端的行为"推演两种协议的取舍；为什么微服务实例默认用 AP？**
   （考察：CAP 不是背概念，是会推演）
3. **OpenFeign 从 @FeignClient 注解到发出 HTTP，中间每一步都有扩展点；请说出至少 3 个扩展点及其生产用途（如超时、重试、鉴权透传、埋点）。**
   （考察：源码链路是否真的走过，还是只会用注解）
