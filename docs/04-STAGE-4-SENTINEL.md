# STAGE 4 · 高并发流量治理与熔断降级

> 旧实现（参考答案）：module-06（@SentinelResource + fallback + QPS 流控实验）。
> 本关升级三件事：① 从"会用控制台"下潜到 **SlotChain 源码架构** ② 首次配置**慢调用熔断** ③ 首次落地 **Feign FallbackFactory**（降级可感知原因、可复用）。

---

## 1. 源码与八股深度剖析

### 1.1 Sentinel 核心责任链 ProcessorSlotChain

每个资源（URL/注解）对应一条槽链，请求依次流过：

```
NodeSelectorSlot        → 给资源建调用树节点（区分入口/调用来源）
ClusterBuilderSlot      → 聚合 ClusterNode（全资源维度的统计容器）
LogSlot                 → 记录限流/熔断日志
StatisticSlot           → ★ 计数：先问后记（放行成功才记 pass，被拒记 block）
AuthoritySlot           → 黑白名单授权
SystemSlot              → 系统自适应保护（CPU/负载/总 QPS 阈值）
ParamFlowSlot           → 热点参数限流（按参数值限，如单 userId 限流）
FlowSlot                → ★ 流控规则检查（对照 1.2 的统计窗口判定）
DegradeSlot             → ★ 熔断规则检查（三态机）
```
记忆要点（面试金句）：**StatisticSlot"先放行后记账"，FlowSlot 拿的是"上一个窗口的账"**——这也是限流存在窗口边界毛刺的根源。

### 1.2 滑动时间窗口 LeapArray

- 把 1 秒切成 `sampleCount` 个小窗口（WindowWrap，默认 2 个 / 窗口 500ms），循环复用数组
- 计数发生在当前窗口；判定时**聚合最近 1s 覆盖到的窗口** → 窗口滑动
- 取舍：窗口越细统计越平滑、内存与 CPU 越贵；QPS 阈值判定的精度上限=窗口宽度
- 你已经见过它的效果：模块 06"1 秒 5 单部分 429"——5 个请求落进同一窗口被计数

### 1.3 令牌桶 vs 漏桶（与 Sentinel 的流控效果对应）

| 算法 | 行为 | 突发流量 | Sentinel 对应 |
|------|------|---------|---------------|
| 令牌桶 | 恒速发令牌，桶内存令牌 | **允许**（攒的令牌可一次消耗） | Warm Up（冷启动：从 阈值/3 渐升到阈值，防冷系统被热流量打死） |
| 漏桶 | 恒定速率流出 | **不允许**（强制整流成匀速） | 排队等待（Threshold/匀速排队，请求在队列中等） |
| 直接拒绝 | 超阈值立刻失败 | 无缓冲 | **快速失败（默认）**——配合熔断最常用 |

### 1.4 熔断器三态流转（DegradeSlot）

```
Closed（正常放行，持续统计）
   │ 慢调用比例 / 异常比例 / 异常数 触发阈值
   ▼
Open（熔断打开：所有请求快速失败，直接走降级）
   │ 等待 recoveryTimeoutMs
   ▼
Half-Open（放 1 个探测请求）
   ├─ 探测成功 → Closed（统计清零）
   └─ 探测失败 → Open（继续熔断）
```
三种策略的判定单位：**慢调用**（RT > 阈值的比例，注意分母是"统计窗口内完成的调用"）、**异常比例**、**异常数**。
生产要点：熔断对象是**调用方视角的资源**（我们用在 Feign 的 `GET /users/{id}` 上最合适）。

### 1.5 OpenFeign 整合 Sentinel 的 FallbackFactory

```java
@FeignClient(name = "user-service", fallbackFactory = UserClientFallbackFactory.class)
public interface UserClient { ... }

@Component
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {
    @Override
    public UserClient create(Throwable cause) {          // ★ 能拿到失败原因
        return new UserClient() {
            public Result<UserDTO> getById(Long id) {
                if (cause instanceof CallNotPermittedException e) { ... } // 熔断打开
                ...返回兜底 DTO（如"降级用户：系统繁忙"）
            }
        };
    }
}
```
`fallback`（业务类内方法）vs `fallbackFactory`（独立类）：
Factory **能感知 cause**（区分 熔断打开/超时/业务异常）、能**跨 Client 复用**、能注入依赖打日志上报——生产一律 Factory。
前置条件（**旧工程没做过**）：`feign.sentinel.enabled=true` + `spring-cloud-starter-alibaba-sentinel`。

---

## 2. 实战任务清单（代码锚点：`// TODO [Stage-4]`）

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 1 | Feign 接入 Sentinel | `order-service` pom（sentinel 已有）+ yml | `TODO [Stage-4]`：开启 `feign.sentinel.enabled=true`，让 Feign 调用成为可保护资源 |
| 2 | **FallbackFactory** | `order-service/.../client/UserClientFallbackFactory.java`（骨架已建） | `TODO [Stage-4]`：按 cause 区分三种降级（熔断打开/读超时/其他），返回不同兜底；`UserClient` 注解加 fallbackFactory 属性 |
| 3 | 慢调用熔断规则 | 控制台配置 + `TODO` 记录参数 | 给 `GET /users/{id}` 配慢调用比例熔断（maxRT、比例、熔断时长），参数说明写进 TODO |
| 4 | 库存接口限流 | `inventory-service` 加 sentinel 依赖（TODO） | 给 `POST /inventory/deduct` 配 QPS 流控（预留 Stage 2 压测联动） |
| 5 | 降级可观测 | FallbackFactory 内 | 降级时打 WARN（含 cause 类型），用 Zipkin/日志验证"降级发生了" |

## 3. 改造实验（Predict → Run → Verify）

1. **三态机亲历**：给 user 的 `/users/1` 加 `Thread.sleep(600)`（慢）→ 配慢调用熔断（maxRT=200ms，比例 50%，熔断 10s）→ 连续请求：预测第几个开始走 fallback？10s 后是否半开恢复？观察日志与控制台熔断态。
2. **FallbackFactory 感知实验**：分别在「user 停机」「熔断 Open」「user 正常但返回错误」三种情况下触发降级 → 打印的 cause 分别是什么类型？
3. **突发的样子**：同一 QPS 阈值下对比"快速失败"与"Warm Up"——压测预热期通过率曲线差异（控制台实时监控截图对比）。

## 4. 本关必考深水区面试题（@面试 4 拷问）

1. **"一个请求进入 Sentinel 后到被限流，经过了哪些 Slot？StatisticSlot 为什么是'先放行后记账'？这种设计会造成什么边界现象？"**
2. **"滑动窗口的 sampleCount 调大调小各有什么代价？如果让你实现 QPS=1000、精度 100ms 的统计，LeapArray 内部怎么设计？"**
3. **"熔断的半开态只放一个请求，如果这个请求恰好在慢调用边缘抖动，会发生什么？生产上怎么避免熔断器'抖动震荡'？"**
