# 模块 04：LoadBalancer 负载均衡 —— 一个服务，多个分身

> 目标：把 user-service 跑成 **两个实例**（8081 和 8083），亲眼看 LoadBalancer
> 把 6 次调用轮流分给它们；再停掉一个实例，看流量如何自动全切到幸存者。
> 模块 03 的 503 实验你记住了"单实例挂=调用方残"，本模块就是第一味解药。

---

## ① 项目背景：为什么要"多分身"

电商大促时 user-service 一台机器扛不住，运维的直觉反应：再起几个（或再加几台机器）。
于是注册中心里 user-service 名下有 N 个实例。新的问题来了：

> order-service 每次调用时，**该把请求发给哪个实例？**

- 不能总发第一个（其余实例闲死）；
- 不能随机乱发（有的实例会被连续命中，有的饿死）；
- 实例挂了要能自动绕开。

这就是**负载均衡（Load Balancing，LB）**。Spring Cloud 的答案是 `Spring Cloud LoadBalancer`——
你在模块 03 已经被迫见过它了（E03-1：没有它 Feign 都起不来）。

## ② 概念文档：客户端负载均衡 vs 服务端负载均衡

### 2.1 两种流派

```
【服务端 LB：Nginx 模式】                 【客户端 LB：Spring Cloud LoadBalancer】
                                        ┌─ order-service 进程内 ─┐
order-service ──► ┌───────────┐         │ Feign → LB 从本地实例列表│
                  │ Nginx      │ 挑一个  │   挑一个 → 直连目标实例  │
                  │ (中间人转发)│ ───────►└───────────────────────┘
                  └─┬───────┬─┘              │            │
                    ▼       ▼                ▼            ▼
                 8081实例  8083实例         8081实例     8083实例
```

| 对比 | 服务端 LB（Nginx） | 客户端 LB（LoadBalancer） |
|------|-------------------|--------------------------|
| 谁来挑实例 | 独立的中间服务器 | **调用方自己**（进程内） |
| 实例列表来源 | Nginx 配置文件（手工维护） | 注册中心自动推送（本项目的 Nacos） |
| 中间一跳 | 有（多一次网络转发） | 无（点对点直连） |
| 微服务适配 | 适合对外入口 | **服务间调用的事实标准** |

Feign + LoadBalancer 的组合：Feign 负责"发什么请求"，LB 负责"发给谁"。

### 2.2 挑选算法：默认轮询（Round Robin）

按实例列表顺序循环分发：A→B→A→B……简单、绝对公平。本模块实验你会看到 8081/8083 严格交替。
其他常见算法：随机（Random）、按权重（性能强的机器多接客）、最少活跃调用（谁闲找谁）。
Spring Cloud LoadBalancer 默认 RoundRobin，可通过自定义 `ReactorServiceInstanceLoadBalancer` 换策略。

### 2.3 架构图：一次 lb-test 请求的内部旅程

```
GET /orders/lb-test
   │
   ▼ order-service
循环 6 次：
   userClient.getInstanceInfo()
      │
      ▼
   LoadBalancer：user-service 的实例列表从哪来？
      ├─ 本地缓存（Nacos 推送的快照）: [192.168.x.1:8081, 192.168.x.1:8083]
      └─ RoundRobin 位置指针 +1 → 取下一个实例
      │
      ▼
   GET http://192.168.x.1:{8081或8083}/users/instance-info
      │
      ▼
   user-service InstanceController 返回自己的端口 → 调用方记录
```

### 2.4 在微服务版图中的位置

LB 是"调用层"的隐形齿轮：藏在 Feign 背后（模块 03），上面将被 Sentinel 保护（模块 06）、
被 Zipkin 追踪（模块 07）。它每调用一次工作一次，但正因为它存在，"扩容"才变成
"多起一个实例"这么轻松的事——**注册中心 + 客户端 LB = 弹性伸缩的地基**。

## ③ 链路分析：lb-test 的完整流转与易错点

| # | 步骤 | 代码位置 |
|---|------|---------|
| 1 | `GET /orders/lb-test` 进入 | [OrderController.java](../order-service/src/main/java/com/example/order/controller/OrderController.java) `loadBalanceTest()` |
| 2 | 循环 6 次调 Feign 接口 | 同上，`userClient.getInstanceInfo()` |
| 3 | Feign 代理 → LB 挑实例 | `RoundRobinLoadBalancer`（日志里见过它！模块03实验的 WARN 就是它打的） |
| 4 | user-service 接请求 | [InstanceController.java](../user-service/src/main/java/com/example/user/controller/InstanceController.java) 返回自己端口 |
| 5 | 6 个端口串成列表返回 | 端口在 8081/8083 交替 = 轮询生效 |

**容易出错的地方**：
1. 第二个实例端口忘了改 → `Port 8081 already in use` 起不来；
2. 第二个实例**没走 IDEA Run Configuration** 而是重复点运行 → IDEA 默认会提示"实例已运行"，要允许多实例（我们的做法：复制配置 + 程序实参，见⑤）；
3. LB 缓存刷新有延迟：刚停掉 8083 的头几秒，可能仍有一两次调用打到它而失败——**调用方缓存 + 心跳剔除的时间窗**（模块 01 讲过），生产靠重试兜底。

## ④ 常见问题与解决思路

### Q1：`No servers available for service: user-service`（模块 03 你已经见过）
LB 的实例列表是空的。排查：Nacos 控制台里 user-service 是否健康在线；调用方刚启动时列表未就绪，重试即可。

### Q2：第二个实例启动报 `Port 8081 was already in use`
说明 `--server.port=8083` 程序实参没生效（比如填到了 VM options 而不是 Program arguments）。
Spring Boot 的命令行参数格式是 `--属性名=值`，会覆盖 application.yml 里的同名配置。

### Q3：为什么轮询不是严格 1:1 交替？
两个实例时基本严格交替。实例多了、或 LB 缓存刚刷新、或有请求失败重试时，统计上会偏离。
另外 Spring Cloud LoadBalancer 默认带实例缓存（你启动日志里那个 Caffeine WARN 提示的就是缓存实现），缓存过期刷新期间可能连续命中同一实例。

### Q4：Nacos 控制台里能给实例设"权重"，怎么让 LB 按权重分发？
Spring Cloud LoadBalancer 默认轮询**不读** Nacos 权重。Spring Cloud Alibaba 提供了
`NacosLoadBalancer`（读取 Nacos 权重做加权轮询），需额外开启配置才能生效——
本模块以理解"策略可替换"为主，权重实验作为选做（README 不展开，有兴趣问导师）。

### Q5：怎么实现自己的挑选策略？（比如总挑最新启动的实例）
实现 `ReactorServiceInstanceLoadBalancer` 接口 + @Configuration 注册到该 Feign 客户端。
面试常问，动手可留到模块 99 综合实战。

## ⑤ 动手实践（15 分钟）

### 第 1 步：重启两个服务的新代码
IDEA：Maven 刷新 → 重启 **UserApplication**（新代码）和 **OrderApplication**（新代码，模块04实验在它里面）。

### 第 2 步：给 user-service 起第二个分身（8083）
IDEA 顶部运行配置下拉框 → **编辑配置** → 选中 `UserApplication` → 点 **复制配置**（⧉ 图标）→
命名为 `UserApplication-8083` → 在 **程序实参**（Program arguments，找不到就点"修改选项"勾选）里填：
```
--server.port=8083
```
→ 确定 → 运行这个新配置。
**预期**：第二个 user-service 起在 8083 端口（同一份代码、同一个库、不同端口）。控制台搜索 `Tomcat started on port 8083` 确认。

### 第 3 步：Nacos 控制台验收
服务列表 → user-service → **实例数 2**，点详情能看到 8081 和 8083 两个实例。

### 第 4 步：见证轮询（高潮）
```bash
curl http://localhost:8082/orders/lb-test
```
**预期**（端口严格交替）：
```json
{"code":0,"data":[
  "第1次调用 → 8081 端口接单",
  "第2次调用 → 8083 端口接单",
  "第3次调用 → 8081 端口接单",
  ...]}
```

### 第 5 步：灾难切换实验
IDEA 停掉 8083 那个实例 → 等 30 秒（心跳剔除+缓存刷新）→ 再跑 `curl http://localhost:8082/orders/lb-test`
**预期**：6 次全是 8081——流量自动全切，用户无感。实验完重启 8083。

### 第 6 步：提交（导师已完成，供对照）
```bash
git commit -m "feat(module-04): 双实例负载均衡演示（RoundRobin）"
```
改动：user-service 新增 InstanceController（实例身份牌）、
order-service UserClient 加 getInstanceInfo、OrderController 加 /orders/lb-test。

## ⑥ 学习检查

**第 1 题（简答）**：Nginx 和 Spring Cloud LoadBalancer 都是负载均衡，它们的本质区别是什么？微服务**内部**调用为什么选后者而不是 Nginx？

**第 2 题（简答）**：第 5 步实验中，停掉 8083 后 LB 是靠哪几个机制知道"它死了、列表该更新了"的？从实例死亡到 order-service 的缓存更新，中间有约多长的"脏窗口"？

**第 3 题（开放）**：如果要求"性能强的实例多接客"（比如 8083 是 4 核机器，应该接 3 倍流量），你会用什么算法？需要哪些信息？（不用写代码，说思路）
