# 🗺️ 五关进阶训练 · 全局路线图

> 前置：本仓库 12 站基础教学已结课（Nacos/Config/Feign/LB/Gateway/Sentinel/Zipkin/RocketMQ/Seata/JWT 全部跑通）。
> 五关不是重复学习，而是**在真实工程上做生产级进阶**。旧代码 = 参考答案，缺口 = 任务。

## 通关进度

- [ ] **Stage 1 · 服务治理与通信基石**（Nacos 深水区 / Feign 源码链路 / 超时与重试边界）
      → 交付：inventory-service 上线注册+被调用；order→inventory 的 Feign 带超时配置
- [ ] **Stage 2 · 分布式幂等与重试风暴防卫**（Redis Token / 防重表 / 状态机 CAS）
      → 交付：下单→扣库存全链路幂等，重复请求/超时重试不再造成重复扣减
- [ ] **Stage 3 · 事务边界陷阱与分布式事务**（@Transactional 失效面 / 事务内 IO 重构 / 本地消息表+对账）
      → 交付：MQ 发送移出事务边界，本地消息表+补偿任务保证事件最终不丢
- [ ] **Stage 4 · 高并发流量治理与熔断降级**（SlotChain / LeapArray / 三态熔断 / FallbackFactory）
      → 交付：order→inventory 慢调用熔断 + FallbackFactory 优雅降级
- [ ] **Stage 5 · 响应式网关与全链路追踪**（Reactor 红线 / GlobalFilter / TraceId 跨线程闭环）
      → 交付：自定义全局过滤器（鉴权透传+染色），跨线程池 TraceId 不丢
- [ ] **结课综合面试**：5 关 × @面试 全走一遍，INTERVIEW-LEDGER 薄弱点清零

## 能力矩阵（每关结课你应能回答）

| 能力 | S1 | S2 | S3 | S4 | S5 |
|------|----|----|----|----|----|
| 画出身兼 Nacos 注册+Feign 调用的完整时序（含超时/重试边界） | ✅ | | | | |
| 设计并落地三层幂等防线（令牌/防重表/状态机）并说清取舍 | | ✅ | | | |
| 指出任意一段代码的事务边界问题并重构（含长事务内嵌 IO） | | | ✅ | | |
| 讲清 Sentinel 一次请求经过哪些 Slot、滑动窗口怎么计数 | | | | ✅ | |
| 解释为什么网关不能阻塞 + TraceId 跨进程/跨线程不丢的闭环 | | | | | ✅ |
| 对应大厂 P6/P7 面试"项目深挖"环节的扛打程度 | 入门 | 资损意识 | 一致性 | 稳定性 | 全局观 |

## 与旧代码/旧教学的关系

| Stage | 依托的旧实现（参考答案） | 本关真正的新任务 |
|-------|--------------------------|------------------|
| 1 | module-01/03/04（已跑通的注册+Feign） | inventory-service 从零接入；order 补 Feign 超时（从未配置过！） |
| 2 | module-08 的 MQ（下游动作异步化） | 三层幂等全新手写：现在 `/orders` 重复点击会重复下单 |
| 3 | module-09 Seata AT + E08-3 教训 | 修复 `OrderService` 事务内发 MQ 的真实反模式 + 本地消息表 |
| 4 | module-06 基础限流 | FallbackFactory、慢调用熔断——旧实现只有注解兜底 |
| 5 | module-05 网关 + module-07 Zipkin | 自定义 GlobalFilter + 跨线程 TraceId 闭环 |

## 指令用法

```
@验收 1    # 闯完第 1 关，验收代码+理解
@埋雷 2    # 在工程里偷偷埋一个幂等相关 Bug，给我报障工单
@面试 3    # 就第 3 关对我进行 3 轮递进拷问
```
