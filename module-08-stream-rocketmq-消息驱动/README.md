# 模块 08：Spring Cloud Stream + RocketMQ 消息驱动 —— 从"打电话"到"发广播"

> 目标：下单成功后，order-service 向 RocketMQ 广播一条"订单已创建"事件；
> user-service 订阅它（打日志，模拟加积分/发通知）。
> 微服务的通信模式从"同步调用"进化出第二条腿：**异步事件**。

---

## ① 项目背景：同步调用的天花板

模块 03~05 的 Feign 调用是**打电话**：我拨号（发请求）、你在线（存活）、我等你回话（阻塞）。
但"下单成功之后要做的事"——加积分、发短信、更新推荐、同步物流——有七八件，
难道下单接口串行打七八个电话？任何一家慢/挂，下单跟着遭殃（模块 03/06 的教训）。

观察这些事后动作的共同点：**下单的人不需要等它们做完，甚至不需要知道谁在做**。
这就是**异步事件**的领地：下单方喊一嗓子"订单 1001 创建啦！"（发消息），关心的人自取（订阅）。
**发布者与消费者：互不等待、互不知道对方存在**——时间解耦 + 空间解耦。

## ② 概念文档：MQ 三板斧 + Stream 的抽象

### 2.1 RocketMQ 核心概念

| 概念 | 类比 | 本项目用法 |
|------|------|-----------|
| **Topic** | 广播频道 | `order-created-topic` |
| **Producer** | 播音的人 | order-service（下单成功后发事件） |
| **Consumer / 消费者组 Group** | 收音机 / 同一部门的收音机组 | user-service（组 `user-service-group`） |
| **NameServer** | 通讯录（谁家 Broker 在哪） | :9876 |
| **Broker** | 实际收发/存储消息的仓库 | :10911 |

**消费者组**是 MQ 精髓：同组多个实例**分摊**消息（集群消费，天然负载均衡，模块 04 思想）；
不同组**各自收全量**（比如积分组和短信组都要收到同一事件）。以后 user-service 扩到 3 个实例，
消息自动分摊——不需要 LoadBalancer，MQ 自带。

### 2.2 Spring Cloud Stream：屏蔽中间件的"插座转换器"

直接用 RocketMQ 客户端 API 写，将来换 Kafka 全部重写。Stream 的思路是**标准插座**：

```
业务代码 → 通道(binding: orderCreated-out-0) → [Binder 绑定器：rocketmq] → Topic
业务代码 ← 通道(binding: orderCreated-in-0)  ← [Binder 绑定器：rocketmq] ← Topic
```

- 业务只面对"通道+函数"：生产用 `StreamBridge.send("orderCreated-out-0", 事件)`，
  消费写一个 `Consumer<事件>` Bean；
- 换 MQ = 换 Binder 依赖 + 改几行 yml，业务代码零改动（ Binder 是唯一知道"底下是谁"的地方）。

### 2.3 架构图

```
┌─ order-service ────────────────┐          ┌─ user-service ─────────────────┐
│ OrderService.create()          │          │ OrderEventConsumer             │
│  ①落库 t_order                 │          │  Consumer<OrderCreatedEvent>   │
│  ②StreamBridge.send(事件) ─────┼──► ┌─────┴──┐ ──────► ③orderCreated()   │
└────────────────────────────────┘    │RocketMQ│           打日志/加积分/发通知 │
   用户拿到响应 ──► 此处早已返回，     │ topic: │           （与下单异步进行）  │
   事件在后台飞                       │ order- │                                │
                                     │created │                                │
                                     └────────┘                                │
```

### 2.4 在微服务版图中的位置

消息驱动是**异步层**：把"必须实时"的留在同步链（Feign 校验用户），"可以晚点"的推向事件
（积分/通知/统计）。它也是**削峰填谷**的神器：秒杀时请求先排队在 MQ，消费端按自己的节奏处理。

## ③ 链路分析：一条"订单创建事件"的一生

```
① POST /orders（走网关）→ validateUser(Feign) → INSERT t_order
② StreamBridge.send("orderCreated-out-0", event)
     → Binder 把事件对象序列化成 JSON
     → 发到 RocketMQ Broker 的 order-created-topic（内存+磁盘，持久化）
③ order-service 立即返回 code:0 给用户（不等消费结果！）
④ Broker 推送消息给 user-service-group
     → Stream 反序列化成 OrderCreatedEvent → orderCreated() Bean 执行 → 日志打出
⑤ Zipkin（模块07）里能看到 order-service 的"发送"Span——消息也会被追踪（Broker 内的消费另计）
```

**容易出错的地方**：
1. **Broker 地址不可达**（最高频！）：Broker 向 NameServer 注册的必须是"宿主机可达地址"——
   我们用 `brokerIP1=host.docker.internal` 解决（[broker.conf](../docker/rocketmq/broker.conf) 注释详述）；
2. **Topic 没建**：依赖 `autoCreateTopicEnable=true` 自动建；生产环境手动建并规划好队列数；
3. 事件 JSON 反序列化失败：两端事件类字段不一致（契约漂移）——所以事件类放 common 共用。

## ④ 常见问题与解决思路

### Q1：发送消息超时 `send message ... timeout` / `connect to broker failed`
九成是 Broker 注册了容器内网 IP。排查：控制台 localhost:8180 → 集群页看 broker 地址是不是
`host.docker.internal:10911`；不是 → 检查 broker.conf 挂载与 brokerIP1，`docker compose down` 后 up 重建。

### Q2：消息发出去了，消费端没收到
排查：① user-service 启动日志有没有绑定成功（搜 `orderCreated-in-0`）；② Topic 名两端拼写一致否；
③ 消费者组是否正常上线（控制台→消费者页）；④ RocketMQ 控制台 → 消息页 → 按 Topic 查，看消息在不在、
被消费几次——**先定位是"没发出"还是"没消费"**。

### Q3：消息会丢吗？
三段都可能丢，各有对策：**生产者**（网络抖动发失败）→ 发送结果同步确认+失败重试；
**Broker**（宕机）→ 刷盘策略 SYNC_FLUSH 或主从复制；**消费者**（刚取到就崩）→ 消费成功后**手动 ACK**，
没 ACK 的消息 Broker 会重投。本模块用默认自动 ACK（打完日志即成功）。

### Q4：同一条消息被消费两次怎么办？
MQ 的投递语义是"**至少一次**"——重复不可避免（网络重试导致）。解药在消费端做**幂等**：
比如按 orderNo 查"积分加过了吗"。记住公式：**至少一次投递 + 消费端幂等 = 业务上恰好一次**。

### Q5：发消息放在 @Transactional 事务里，有没有坑？
有：消息可能在**事务提交前**就飞出去了，消费者拿到事件却查不到订单（脏读窗口）。
生产方案："事务消息"（RocketMQ 半消息机制）或"本地消息表"。本模块先埋个认知，模块 09 Seata 后可回看。

## ⑤ 动手实践（25 分钟）

### 第 1 步：启动 RocketMQ 三件套
```bash
docker compose -f docker/docker-compose.yml up -d
docker ps        # 应看到 namesrv / broker / dashboard 三个新容器
```
控制台 http://localhost:8180 （集群页能看到 broker-a = host.docker.internal:10911）。

### 第 1.5 步：手动创建 Topic（重要！）
自动建 Topic 在容器版 5.x 上不生效（踩坑实录见错误知识库 E08-3），生产规范也是手动建：
```bash
docker exec micro-rocketmq-broker sh mqadmin updateTopic -n rocketmq-namesrv:9876 -b 192.168.85.1:10911 -t order-created-topic -r 8 -w 8
# 验证路由已生成（能看到 brokerDatas 即成功）
docker exec micro-rocketmq-namesrv sh mqadmin topicRoute -n localhost:9876 -t order-created-topic
```

### 第 2 步：重启两个服务（Maven 刷新后）
IDEA 重启 UserApplication、OrderApplication。启动日志搜 `orderCreated` 确认绑定成功。

### 第 3 步：下单并见证"广播"
```bash
printf '{"userId":1,"productName":"消息驱动测试","amount":88.00}' > /tmp/order.json
curl -X POST http://localhost:8080/api/orders -H "Content-Type: application/json; charset=utf-8" --data-binary @/tmp/order.json
```
立刻看 **user-service 的 IDEA 控制台**——会出现：
```
[MQ] 收到订单创建事件：orderNo=ORD...，userId=1，商品=消息驱动测试，金额=88.00（此处可扩展：加积分/发通知）
```
**下单响应早就返回了，事件在后台自己飞到了 user-service**——这就是异步。

### 第 4 步：RocketMQ 控制台验收
localhost:8180 → 消息 → Topic 选 `order-created-topic` → 查到消息、消费状态 OK。

### 第 5 步：提交（导师已完成，供对照）
```bash
git commit -m "feat(module-08): 集成 Stream+RocketMQ，下单广播订单创建事件"
```

### 选做进阶
① Zipkin 看消息 Span；② 给 user-service 再起一个实例 → 下单两单 → 观察消息被两个实例**分摊**（同组负载均衡）；③ 停掉 user-service 再下单 → 重启后消息**补投**（离线消息不丢）。

## ⑥ 学习检查

**第 1 题（简答）**：Feign 和 MQ 都能完成"服务间协作"，各自的本质区别是什么？"下单后发短信"该用哪个？"下单前校验用户"呢？为什么？

**第 2 题（简答）**：消息可能在哪里丢失？各段的对策是什么？

**第 3 题（简答）**：为什么 MQ 一定会有"重复消费"？消费端怎么做到"业务上不重复"？（结合我们的积分场景说思路）
