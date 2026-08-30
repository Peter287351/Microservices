# 模块 06：Sentinel 熔断降级 —— 给下单接口装上"保险丝"

> 目标：给下单接口加**流控规则**（每秒最多 1 单），超限请求自动走**兜底提示**而不是报错；
> 顺手理解**熔断降级**如何治好模块 03 你亲身体验过的"级联失败"。

---

## ① 项目背景：光有轮询还不够

模块 04 的多实例解决了"一台不够用"，但两种场景它无能为力：
1. **流量洪峰**：秒杀瞬间 QPS 冲到 1000，10 个实例也得被压垮——全站雪崩；
2. **下游病了**：user-service 还活着但响应 5 秒，order-service 的线程全被拖住等它——**级联失败**（模块 03 实验的加强版）。

Sentinel（阿里开源的"流量防卫兵"）提供三层保护，全部围绕一个概念——**资源**：
- **限流**：每秒最多放 N 个请求进来，超额直接拒绝（快速失败，不拖垮自己）；
- **熔断**：下游"病了"（慢/错太多）就**暂时不去调它**，直接走兜底，过会儿再试探（半开）；
- **降级**：拒绝/熔断发生时，返回**预设的兜底结果**（友好提示、缓存数据），而不是裸报错。

## ② 概念文档：资源、规则、控制台

### 2.1 什么是"资源"

一切可保护的代码块。两种定义方式：
- **URL 资源（自动）**：引依赖后每个 Controller 方法自动成为一个资源（如 `/orders`）；
- **注解资源（手动）**：`@SentinelResource("createOrder")` 给方法起个业务名（我们用在下单接口上，[OrderController.java](../order-service/src/main/java/com/example/order/controller/OrderController.java)）。

### 2.2 两种最常用的规则

**流控规则（FlowRule）**——限流：
| 阈值类型 | 含义 | 例子 |
|---------|------|------|
| QPS | 每秒**进入**的请求数 | 秒杀接口限 100 QPS |
| 并发线程数 | 同时**处理中**的请求数 | 下单要调 3 个下游，限并发防拖垮 |

流控效果：快速失败（默认，直接拒绝）/ Warm Up（冷启动慢慢放量）/ 排队等待（匀速通过）。

**熔断降级规则（DegradeRule）**——熔断：三种触发策略——**慢调用比例**（响应超 1s 的占比超 50%）、**异常比例**（出错占比超 50%）、**异常数**（1 分钟错 20 次）。状态机：

```
闭合(正常放行) ──触发条件──► 打开(全部快速失败,走fallback) ──等N秒──► 半开(放一个试探)
      ▲                                                    │试探成功→闭合
      └────────────────试探失败→继续打开◄──────────────────┘
```

### 2.3 架构图：一次下单的"安检"流程

```
POST /orders
   │
   ▼
Sentinel 拦截（createOrder 资源）
   │ 滑动窗口统计当前 QPS
   ├─ 未超阈值 ──► 放行 → orderService.create() → 正常返回 code:0
   └─ 超阈值 ────► 抛 BlockException → 自动调用 fallback
                    → 返回 {"code":429,"message":"下单太火爆啦..."}（友好降级）
所有统计/规则在控制台（8858）可视化，服务每 5 秒上报心跳
```

### 2.4 在微服务版图中的位置

Sentinel 守在**调用链的每一道关口**：网关层（模块 05 的路由可限流）、服务接口层（本模块）、
Feign 调用层（对 user-service 的调用做熔断——`feign.sentinel.enabled=true` 一行就能让
模块 03 的 503 裸报错变成优雅降级，实践选做）。它和 Nacos 一样来自 Spring Cloud Alibaba 全家桶。

## ③ 链路分析：一次被限流的下单

```
第 N 次 POST /orders（1 秒内）
   │
   ▼
@SentinelResource("createOrder") 切面拦截（AOP 代理，还记得模块03的动态代理吗？同一个家族）
   │ 查规则：createOrder 的 QPS 阈值 = 1
   ├─ 本秒是第 1 个请求 → 放行 → 正常下单
   └─ 本秒第 2+ 个请求 → BlockException/直接进 fallback
                          └─ createOrderFallback() → {"code":429,"下单太火爆啦"}
```

**容易出错的地方**：
1. **控制台看不到服务**：Sentinel 控制台是**懒加载**——服务启动后要**至少被请求一次**，才会出现在控制台（心跳才上报）；
2. **fallback 不生效**：兜底方法签名必须是"原参数 + 末尾 Throwable"，且与 @SentinelResource 在**同一个类**；写错签名静默失效；
3. **注解资源没被代理**：类内部 `this.create()` 自调用不走 AOP 代理，注解失效（必须从外部调进来）。

## ④ 常见问题与解决思路

### Q1：控制台登录后一片空白，没有我的服务
懒加载+心跳延迟：先随便请求几次下单接口，等 5~10 秒刷新"实时监控"。仍不出现 → 检查 yml `spring.cloud.sentinel.transport.dashboard: localhost:8858`，容器日志 `docker logs micro-sentinel`。

### Q2：配了流控规则却不生效
① 资源名对不上（控制台配的是 URL 资源 `/orders`，代码注解的是 `createOrder`——两个是不同资源！给哪个配规则就只对哪个生效）；② 规则配给了没被调用的资源。看"簇点链路"页，**里面列出的才是活的资源**，对它配规则必中。

### Q3：fallback 方法从不执行
签名不匹配（必须原参数+Throwable 结尾）、不在同一个类、或触发的是"异常"而 fallback 其实是 blockHandler 的职责（@SentinelResource 里 blockHandler 管**限流/熔断**，fallback 管**业务异常**，两者都可配，我们统一用 fallback 兼顾）。

### Q4：规则重启就没了
Sentinel 规则默认存**服务内存**（轻量但易失）。生产方案：把规则推送到 Nacos 持久化，服务启动时从 Nacos 拉取——**模块 02 的配置中心知识直接复用**，这正是"为什么先学配置中心"的答案之一。

### Q5：控制台版本与客户端兼容性
我们用 dashboard 1.8.0（bladex 镜像）+ 客户端 1.8.6（SCA 2023.0.1.0 带的），基础流控/熔断完全兼容。若用新特性遇兼容问题，优先让 dashboard 版本 ≥ 客户端版本。

## ⑤ 动手实践（20 分钟）

### 第 1 步：启动 Sentinel 控制台
```bash
docker compose -f docker/docker-compose.yml up -d sentinel-dashboard
```
浏览器 http://localhost:8858 （账号密码 sentinel/sentinel）。首启较慢，等 1 分钟。

### 第 2 步：重启两个服务（Maven 刷新后）
IDEA 重启 UserApplication、OrderApplication。

### 第 3 步：让服务在控制台"现形"（懒加载！）
```bash
# 随便下两单（正常userId=1），触发心跳上报
printf '{"userId":1,"productName":"测试","amount":9.90}' > /tmp/order.json
curl -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" --data-binary @/tmp/order.json
```
刷新控制台 → 左侧出现 order-service → **簇点链路** → 能看到 `createOrder`（注解资源）和 `/orders`（URL 资源）。

### 第 4 步：配流控规则（高潮）
簇点链路 → 找到 `createOrder` → 点"**流控**" → 阈值类型 QPS、单机阈值 **1** → 确定。

### 第 5 步：压测见证限流
```bash
# 1 秒内连发 5 单（&后台并发执行）
for i in 1 2 3 4 5; do curl -s -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" --data-binary @/tmp/order.json & done; sleep 2; echo
```
**预期**：5 个响应里部分 `code:0` 成功、部分 `{"code":429,"message":"下单太火爆啦..."}`。
**对照**：没有 Sentinel 时，这 5 单会全部砸进业务+数据库。**把两种响应发我**。

### 第 6 步：提交（导师已完成，供对照）
```bash
git commit -m "feat(module-06): 集成 Sentinel 限流熔断（下单接口+兜底降级）"
```
改动：两服务加 sentinel starter + dashboard 地址、docker-compose 加控制台、
OrderController 加 @SentinelResource 与 fallback。

### 选做进阶
① 熔断实验：给 `createOrder` 配"异常比例"熔断，然后连发 userId=999 的单（100% 异常）观察打开→半开；② Feign 级联保护：order-service yml 加一行 `feign.sentinel.enabled: true`，停掉 user-service 再下单——503 裸报错会变成可被 Sentinel 接管的资源（模块 06 的完全体）。

## ⑥ 学习检查

**第 1 题（简答）**：流控规则的 QPS 阈值和并发线程数阈值有什么区别？分别适合保护什么样的接口？

**第 2 题（简答）**：熔断器的三态（闭合→打开→半开）是如何循环的？它解决了模块 03 里的哪个具体问题？

**第 3 题（简答）**：为什么 Sentinel 的规则默认"重启就丢"？生产上怎么解决？（提示：想想模块 02）
