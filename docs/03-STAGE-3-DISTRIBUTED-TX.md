# STAGE 3 · 事务边界陷阱与分布式事务

> 旧实现（参考答案）：module-09 的 Seata AT（全局回滚实验满分）+ module-08 的 MQ。
> 本关的两个"真"：
> ① 旧工程里**真的埋着一个反模式**——`OrderService.create` 在 `@GlobalTransactional` 内调 `StreamBridge.send`（事务内 IO）；
> ② `@Transactional` 失效场景你只踩过"找不到符号"，还没踩过"事务根本没生效"。

---

## 1. 源码与八股深度剖析

### 1.1 @Transactional 失效场景全集（面试必考 Top3 场景）

| 失效场景 | 根源 | 本仓库对应物 |
|----------|------|-------------|
| **同类自调用** | `this.method()` 不走代理，AOP 切面（事务拦截器）根本没机会介入 | Sentinel 的 `@SentinelResource` 同理——我们注释里写过"注解资源没被代理就失效" |
| 方法非 public | Spring 事务 AOP 默认只代理 public 方法 | —— |
| 异常被 try-catch 吞掉 | 事务回滚靠异常触发，吞了=告诉 Spring 一切正常 | E08-3 我们故意的：MQ 失败被 catch，事务继续提交（当时是韧性设计） |
| rollbackFor 缺省 | 默认只回滚 `RuntimeException/Error`，受检异常不回滚 | `@GlobalTransactional(rollbackFor = Exception.class)` 我们写全了，本地事务呢？ |
| 多线程 | 事务绑定 ThreadLocal 连接，子线程里是另一个连接=另一个事务 | Stage 5 的 TraceId 跨线程是同一个根源家族 |

### 1.2 长事务内嵌 RPC → 连接池打满（标准事故链）

```
一个"长事务"：BEGIN → UPDATE → 调远程接口(耗时 3s) → INSERT → COMMIT
    └─ 整个期间独占 1 个数据库连接（连接是事务的载体，不能中途还给池）

并发 200 请求 × 每个占连接 3s+  →  HikariCP(max=10) 瞬间打满
    → 第 11 个请求开始等待 connection-timeout(默认 30s) → 超时
    → 等待队列积压 → Tomcat 线程池也被拖满 → 整个服务所有接口（哪怕不碰DB）全部僵死
    → 上游 Feign 读超时 → 上游重试 → 雪崩放大
```
面试金句：**事务的边界 = 连接的占用时长；RPC 是事务内的定时炸弹**。
正确姿势：RPC 移出事务；确需"一起成功"→ 编排成多个短事务 + 一致性方案（Seata/消息表），而不是拉长一个事务。

### 1.3 CAP 与 BASE

- CAP：分区（P）在分布式里必然发生，所以实际是 **CP（拒绝服务保一致）vs AP（容忍过期保可用）** 的选择——Stage 1 的 Distro/Raft 是同一课的存储层版本
- BASE：基本可用（Basically Available）+ 软状态（Soft state：允许中间态如"处理中"）+ 最终一致（Eventually consistent）
- 本地消息表就是 BASE 的教科书实现：业务与消息**同事务**落库（软状态=PENDING）→ 异步投递 → 对账兜底 → 最终一致

### 1.4 Seata AT 深水区（补模块 09 没讲透的三点）

1. **全局锁**：TC 按 `资源ID(表名) + 行主键` 记录锁。两个全局事务改**同一行**时，后注册的分支必须等前者释放（二阶段结束）——这是 AT 写隔离的根基，也是热点行吞吐瓶颈的根源（秒杀题的答案）
2. **Before/After Image 与防脏写**：回滚前 RM 校验**后镜像 == 当前数据库值**；不一致=有人绕过全局事务改了数据 → 回滚失败转人工（`rollback failed, dirty data`）。这就是为什么 AT 保护的表**严禁绕过 Seata 直改**
3. **undo_log 的生命周期**：与业务 SQL 同一本地事务写入（保证镜像与数据原子可见）→ 全局提交后异步删除 → 回滚时读取镜像补偿后再删。你模块 09 日志里的 `undo_log deleted with GlobalFinished` 就是终章

### 1.5 本地消息表 + MQ 最终一致 + 对账兜底

```
业务事务：UPDATE t_order + INSERT t_local_message(status=PENDING)   ← 同库同事务，原子
投递器：  定时任务扫 PENDING → 发 MQ → 成功改 SENT（或 MQ 发送回执驱动）
消费端：  幂等消费（Stage 2 的防线） → 处理成功 ACK
兜底对账：对账任务比对 业务表 vs 消息表（PENDING 超时未_SENT 重发；SENT 超时未消费告警）
```
对比 Seata：Seata=同步强一致（用户在等结果），消息表=异步最终一致（用户不等）。
我们的场景正合适：**下单事件本来就不该阻塞下单响应**（模块 08 的 MQ 失败只记日志，就是缺少消息表的"最终不丢"承诺）。

---

## 2. 实战任务清单（代码锚点：`// TODO [Stage-3]`）

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 1 | **指认反模式** | `order-service/.../service/OrderService.java` | `TODO [Stage-3]` 已标在 `streamBridge.send` 处：先写 200 字说明它错在哪（事务边界/连接占用/消息与订单的原子性） |
| 2 | 本地消息表落地 | `02-stage-init.sql` 的 `t_local_message`（已建）+ `order-service` 新增 `LocalMessage` 实体/Mapper（MP） | 表结构已给：message_id/topic/payload/status/retry_count/next_retry_at |
| 3 | 业务与消息同事务 | 重构 `OrderService.create` | `TODO [Stage-3]`：事务内只做 UPDATE 订单 + INSERT 消息表；`StreamBridge.send` 移出事务 |
| 4 | 投递器 | `order-service/.../messaging/LocalMessagePublisher.java`（骨架已建） | 定时扫 PENDING → 发 MQ → 改 SENT；失败退避重试（retry_count/next_retry_at 用起来） |
| 5 | 消费端幂等对接 | `user-service/.../OrderEventConsumer` | 用 Stage 2 的防重表思想按 messageId 幂等 |
| 6 | 对账兜底任务 | `LocalMessagePublisher` 内再加一个方法 | 扫"SENT 超时未确认/重试超 N 次"→ 告警日志（学习版），生产=告警+人工 |

## 3. 改造实验（Predict → Run → Verify）

1. **连接池打满复现**：临时在事务里加 `Thread.sleep(2000)`（模拟 RPC），HikariCP 池调小（`maximum-pool-size: 4`）→ 10 并发打下单 → 观察其余接口（不碰 DB 的）是否也被拖死。验证后还原。
2. **消息原子性实验**：改造前，用"炸弹"回滚一笔订单 → 查 RocketMQ 控制台：事件发出去了吗？订单却不存在（**幽灵消息**）。改造后重放 → 消息与订单同生共死。
3. **投递可靠性实验**：改造后停掉 RocketMQ broker → 下单 → 订单成功+消息表 PENDING；重启 broker → 定时任务自动补投 → user-service 收到。**这就是"最终不丢"的体感**（对比模块 08 只能记日志的无奈）。

## 4. 本关必考深水区面试题（@面试 3 拷问）

1. **"HTTP 调用放事务里到底会出什么事？从连接池角度给我推演一遍雪崩全过程。"**
2. **"Seata AT 的全局锁和 MySQL 行锁是什么关系？两个全局事务改同一行的完整时序（含等待、超时、回滚）画给我。"**
3. **"你的下单链路同时有 Seata（扣款同步强一致）和本地消息表（积分异步最终一致），为什么选两种方案而不是只用一种？"**
