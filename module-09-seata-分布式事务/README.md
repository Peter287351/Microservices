# 模块 09：Seata 分布式事务 —— 两个服务的两次写，同生共死

> 目标：下单 = **扣账户余额（user-service）+ 创建订单（order-service）**，两个服务的两次数据库写
> 要么都成功、要么都回滚。用"炸弹"商品注入失败，亲眼看着钱被退回、订单消失。
> 这是全课程最硬核的一站，也是前 8 站所有知识的会师。

---

## ① 项目背景：最后一座大山——数据一致性

现在的下单链路：`扣款（user-service 写 t_account）→ 建订单（order-service 写 t_order）`。
两个服务、两个本地事务。致命场景：**扣款成功后，建订单突然失败**（数据库抖动、风控拦截、进程崩溃）——
结果：**钱扣了，单没建**。用户投诉，对账救火。

这就是**分布式事务**问题：跨服务的多次写，如何保证"要么全成，要么全不成"。
Seata 是阿里开源的分布式事务解决方案，AT 模式对代码**近乎零侵入**——加一个注解就够了。

## ② 概念文档：三角色 + AT 模式两阶段

### 2.1 三个角色（记住缩写，面试必考）

| 角色 | 全称 | 干什么 | 本项目里是谁 |
|------|------|--------|-------------|
| **TC** | Transaction Coordinator 事务协调器 | 维护全局事务状态，指挥提交/回滚 | seata-server 容器（:8091） |
| **TM** | Transaction Manager 事务管理器 | 定义全局事务边界：开启/提交/回滚 | order-service（@GlobalTransactional 所在方） |
| **RM** | Resource Manager 资源管理器 | 管理分支事务，向 TC 注册分支并执行补偿 | user-service、order-service（每个连库的服务） |

### 2.2 AT 模式（Auto Transaction）两阶段原理

**核心思想：把"分布式事务"拆成多个"本地事务"+ 一张回滚日志表（undo_log）。**

```
一阶段（每个分支各自执行，不互相阻塞）：
  RM 拦截业务 SQL → 查询改动前的数据快照（前镜像）
  → 执行业务 SQL → 查询改动后的数据快照（后镜像）
  → 前后镜像+行锁信息写入 undo_log 表
  → 本地事务【直接提交】（和 undo_log 写入在同一个本地事务里）
  → 向 TC 注册分支、上报状态

二阶段（TC 根据全局事务结果指挥）：
  全局提交 → 各分支异步删除 undo_log（快照没用了）
  全局回滚 → 各分支按 undo_log 的前镜像生成反向 SQL 补偿
           （比如 UPDATE 后余额 9933 → 补偿回 10000；INSERT 后 → DELETE）
```

**为什么 AT 快？** 一阶段就提交了本地锁（对比 XA 两阶段全程持锁），
靠 undo_log 镜像补偿 + **全局锁**（防两个全局事务同时改同一行）实现隔离性。
代价：回滚是"补偿"而非"真回滚"——中间有短暂的数据可见窗口，且要求业务表**必须能被反向 SQL 重建**
（有唯一约束冲突的场景不适用 → 那就要用 TCC/SAGA 模式，见④Q5）。

### 2.3 架构图：一次"炸弹下单"的全局回滚

```
POST /orders {"userId":1,"productName":"炸弹键盘",...}
   │
   ▼
┌─ order-service（TM）────────────────────────────────────────────┐
│ @GlobalTransactional 开启 → 向 TC 申请全局事务 XID              │
│ ① Feign 扣款（请求头带 XID）──► user-service(RM)                │
│      UPDATE t_account SET balance=9933                          │
│      写 undo_log(前镜像:10000, 后镜像:9933) → 本地提交 → 注册分支 │
│ ② INSERT t_order                                                │
│      写 undo_log → 本地提交 → 注册分支                           │
│ ③ 商品名含"炸弹" → 抛异常！                                     │
│    TM 向 TC 报告：全局回滚！                                     │
└─────────────────────────────────────────────────────────────────┘
   TC 指挥两个分支回滚：
      order 分支 → 按 undo_log 删掉刚 INSERT 的订单
      user  分支 → 按 undo_log 把余额 9933 补偿回 10000
   结果：订单没了，钱回来了 —— 数据一致 ✅
```

### 2.4 在微服务版图中的位置

一致性层（模块 09）与可靠性三兄弟配合：Sentinel 防"调用雪崩"（模块 06）、
MQ 异步防"主流程被拖垮"（模块 08）、Seata 防"跨服务数据撕裂"。三者合体，
才算把分布式最难的"不可靠网络下的正确性"补齐。

## ③ 链路分析：代码调用点与 XID 传播

| # | 步骤 | 代码位置 |
|---|------|---------|
| 1 | TM 开启全局事务 | [OrderService.java](../order-service/src/main/java/com/example/order/service/OrderService.java) `@GlobalTransactional` |
| 2 | 分支①扣款 | `userClient.deductBalance()` → [UserClient.java](../order-service/src/main/java/com/example/order/client/UserClient.java) → [AccountController](../user-service/src/main/java/com/example/user/controller/AccountController.java) → [AccountService.deduct](../user-service/src/main/java/com/example/user/service/AccountService.java) |
| 3 | 分支②订单落库 | `orderRepository.save()` |
| 4 | 失败注入 | 商品名含"炸弹"→ 抛 `BusinessException` |
| 5 | 全局回滚 | TM→TC→两分支按 undo_log 补偿 |

**XID 怎么传播**：`spring-cloud-starter-alibaba-seata` 自动给 Feign 加拦截器——TM 发起调用时
把 XID 写进请求头（`TX_XID`），下游 RM 读到后把本地分支挂到这个全局事务上。
这也解释了④的坑：**换了调用方式（如手动 RestTemplate）XID 就断了**。

**容易出错的地方**：
1. **undo_log 表没建**：RM 写镜像直接报错（每个参与库都要建，我们共库所以建一张）；
2. **vgroup-mapping / grouplist 配错**：连不上 TC（`cannot get server address`）；
3. **XID 没传下去**：下游分支没挂进全局事务，回滚只回滚了一半——比不回滚更可怕。

## ④ 常见问题与解决思路

### Q1：启动/下单报 `Table 'micro.undo_log' doesn't exist`
AT 模式的命根子。每个参与全局事务的库都要有 undo_log 表（建表 SQL 在
[docker/mysql/init/01-init.sql](../docker/mysql/init/01-init.sql)，现有环境手动执行一次即可，README ⑤有命令）。

### Q2：报 `cannot get server address` / `connect to seata server failed`
客户端找不到 TC。排查：① seata-server 容器活着吗（`docker ps`）；② yml 里
`seata.service.grouplist.default: 127.0.0.1:8091` 端口对不对；③ `tx-service-group` 与
`vgroup-mapping` 的 key 必须一字不差（`default_tx_group`）。

### Q4：只回滚了一半（比如订单没了但钱没回来）
XID 传播断了：下游分支没加入全局事务。检查 Feign 是否被 Seata 增强（依赖没引错）、
下游 yml 有没有 seata 配置；用 Zipkin/日志确认下游请求头里有没有 XID。

### Q3（先记现象）：为什么"炸弹"下单返回 500 风控拦截而不是 429？
模块 09 顺手把 fallback 精细化了（[OrderController.java](../order-service/src/main/java/com/example/order/controller/OrderController.java)）：
业务异常（风控拦截、余额不足）**如实上抛**给全局异常处理器返回真实错误码；
只有限流/熔断/基础设施异常才给友好兜底——上一模块"429 伪装"问题的正式修正。

### Q5：AT 模式什么场景不适用？
① 高并发热点行（全局锁串行化，吞吐骤降）；② 长事务（镜像保留久、锁等待久）；
③ 反向 SQL 无法执行的业务（如触发器、无唯一键的大表更新）。替代：**TCC**（手工编写
Try/Confirm/Cancel 三个方法，性能最好侵入最大）、**SAGA**（长流程状态机）。选型口诀：
默认 AT，热点 TCC，长流程 SAGA，能不用分布式事务就设计成最终一致（模块 08 的 MQ 就是）。

## ⑤ 动手实践（25 分钟）

### 第 1 步：启动 Seata TC + 建 undo_log
```bash
docker compose -f docker/docker-compose.yml up -d seata-server
# 现有数据库手动建 undo_log（全新数据卷会由 init 脚本自动建）
docker exec micro-mysql mysql -uroot -proot123 -e "source /dev/stdin" micro <<'EOF'
CREATE TABLE IF NOT EXISTS undo_log (
  `branch_id` BIGINT NOT NULL, `xid` VARCHAR(128) NOT NULL,
  `context` VARCHAR(128) NOT NULL, `rollback_info` LONGBLOB NOT NULL,
  `log_status` INT NOT NULL, `log_created` DATETIME(6) NOT NULL,
  `log_modified` DATETIME(6) NOT NULL, UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
EOF
```
控制台 http://localhost:7091 （seata/seata）能看到 TC 信息。

### 第 2 步：重启两个服务（Maven 刷新后）
启动日志搜 `register TM success` / `register RM success`（或无 seata 报错即正常）。

### 第 3 步：查初始余额
```bash
curl "http://localhost:8080/api/users/1"            # 确认用户存在
curl -X POST "http://localhost:8080/api/accounts/deduct?userId=1&amount=0.01"   # 触发开户（余额变 9999.99）
curl "http://localhost:8080/api/accounts/balance?userId=1"   # 记下余额，比如 9999.99
```

### 第 4 步：实验 A —— 正常下单（全局提交）
```bash
printf '{"userId":1,"productName":"正常商品","amount":66.60}' > /tmp/order.json
curl -X POST http://localhost:8080/api/orders -H "Content-Type: application/json; charset=utf-8" --data-binary @/tmp/order.json
```
**预期**：code:0；余额 = 9999.99 - 66.60 = **9933.39**；订单存在。

### 第 5 步：实验 B —— 炸弹下单（全局回滚，高潮）
```bash
printf '{"userId":1,"productName":"炸弹键盘","amount":50.00}' > /tmp/order_bomb.json
curl -X POST http://localhost:8080/api/orders -H "Content-Type: application/json; charset=utf-8" --data-binary @/tmp/order_bomb.json
# 预期：{"code":500,"message":"风控拦截：模拟下单失败，验证全局回滚"}
curl "http://localhost:8080/api/accounts/balance?userId=1"
curl "http://localhost:8080/api/orders?userId=1"
```
**预期**：余额**还是 9933.39**（50 元被退回！）；订单列表里**没有**"炸弹键盘"。
（想看反面对照：把 @GlobalTransactional 注释掉再炸一次——钱扣了单没建。看完记得恢复。）

### 第 6 步：提交（导师已完成，供对照）
```bash
git commit -m "feat(module-09): 集成 Seata AT 模式（下单+扣款全局事务）"
```

### 选做进阶
Seata 控制台 localhost:7091 看全局事务列表；下单瞬间用 Navicat 盯 undo_log 表——
能看到镜像闪现又消失（提交）或被消费（回滚）。

## ⑥ 学习检查

**第 1 题（简答）**：用自己的话讲 AT 模式两阶段：一阶段各分支做了什么？二阶段提交/回滚分别做什么？"undo_log 前镜像"在回滚时起什么作用？

**第 2 题（简答）**：TC / TM / RM 在我们"炸弹下单"场景里分别对应什么？XID 是怎么从 order-service 传到 user-service 的？

**第 3 题（开放）**：秒杀场景 1 万人抢 1 件商品（同一行库存的高频扣减），AT 模式会卡在哪？你倾向换哪种模式或干脆换设计（比如模块 08 的 MQ + 最终一致）？
