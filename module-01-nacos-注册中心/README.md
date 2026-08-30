# 模块 01：Nacos 注册中心 —— 服务之间的"电话簿"

> 目标：两个服务启动后自动把自己"登记"到 Nacos，并在控制台看到两盏绿灯；
> 再通过 `/discovery` 接口亲眼验证"服务发现"真的能查到对方。
> **本模块开始，这个项目才真正踏入微服务世界。**

---

## ① 项目背景：为什么现在需要它

回忆模块 00 的架构：user-service 和 order-service 是**两个互不理睬的进程**，下单根本不校验用户（OrderService 里那个 TODO）。

模块 03 我们要让 order-service 调用 user-service。第一个问题立刻出现：

> **order-service 怎么知道 user-service 在哪？**

它只认识 `name: user-service` 这个名字，不知道 IP 和端口。硬编码 `localhost:8081` 行不行？行，但有三个死穴：
1. user-service 换机器/换端口/扩容到 3 个实例，order-service 的代码就要跟着改；
2. 无法感知对方死没死——调一个挂掉的地址只会白白超时；
3. 实例一多没法分流。

**注册中心（电话簿）就是解法**：每个服务启动时"报户口"（注册），需要找人时"查号"（发现）。Nacos 是阿里巴巴开源的注册中心 + 配置中心，Spring Cloud Alibaba 生态的事实标准。

## ② 概念文档：核心概念、原理与架构图

### 2.1 三分钟理解注册中心

| 概念 | 类比 | 在本项目中的样子 |
|------|------|-----------------|
| 服务提供者 Provider | 登记电话的人 | user-service、order-service（都既是提供者也是消费者） |
| 服务注册 | 报户口：登记姓名+住址 | 启动时把 `user-service → 192.168.x.x:8081` 写进 Nacos |
| 服务发现 | 查电话簿 | `GET /discovery/user-service` 查到它的 IP:端口 |
| 心跳 | 每 5 秒报一次"我还活着" | Nacos 客户端自动发送，超时未报标记为不健康，15~30 秒剔除 |
| 注册表 | 电话簿本身 | Nacos 内存里的服务-实例映射表（持久化+集群同步） |

### 2.2 架构图（文字版）

```
┌────────────────┐   ①启动时注册(名字+IP+端口)   ┌──────────────────────┐
│  user-service  │ ──────────────────────────► │                      │
│  :8081         │                             │   Nacos 注册中心      │
│                │   ②每5秒心跳"我还活着"        │   (Docker :8848)     │
│                │ ──────────────────────────► │   ┌────────────────┐ │
└────────────────┘                             │   │ 服务注册表:     │ │
                                               │   │ user-service   │ │
┌────────────────┐   ①②同上                    │   │  → 8081 实例   │ │
│  order-service │ ──────────────────────────► │   │ order-service  │ │
│  :8082         │                             │   │  → 8082 实例   │ │
│                │   ③订阅：user-service的实例   │   └────────────────┘ │
│                │ ◄────────────────────────── │  变化时主动推送更新    │
└────────────────┘                             └──────────────────────┘
```

关键机制两点：
- **客户端缓存**：order-service 查到 user-service 的实例列表后会**缓存在本地内存**，之后调用直接用缓存，不是每次都去问 Nacos——电话簿抄一份在手里，电话簿丢了也能打电话；
- **推拉结合**：Nacos 通过 UDP/gRPC 推送实例变化 + 客户端定期拉取兜底，保证缓存最终一致。

### 2.3 在微服务版图中的位置

```
【基础设施层】Nacos(注册+配置) ── 所有服务都依赖它，第一个部署
【通信层】    OpenFeign + LoadBalancer(模块03/04) ── 消费注册中心
【入口层】    Gateway(模块05)
【保护层】    Sentinel(模块06)  【观测层】Zipkin(模块07)
【异步层】    RocketMQ(模块08)  【一致性】Seata(模块09)  【安全】JWT(模块10)
```

Nacos 是**第一个上场的中间件**，因为后面几乎每个组件都要用它（Gateway 路由转发按服务名找目标、Sentinel 拉规则、Seata 当协调者……全都通过注册中心找人）。

## ③ 链路分析：本模块的两条关键链路

### 链路 A：服务注册（启动瞬间发生了什么）

```
1. IDEA 里点 Run
2. Spring Boot 启动 → 加载 application.yml → 发现 spring.cloud.nacos.discovery 配置
3. NacosServiceRegistry.register() 通过 gRPC(端口9848) 发送注册请求：
   "我是 user-service，地址 192.168.x.x:8081"
4. Nacos 服务端写入注册表，返回成功 → 日志打印
   "nacos registry, DEFAULT_GROUP user-service x.x.x.x:8081 register finished"
5. 之后每 5 秒：心跳线程上报；Ctrl+C 停服时：主动注销（下线）
```

代码调用点：这一切由 `spring-cloud-starter-alibaba-nacos-discovery` 自动装配，**我们写的代码零改动**——这就是 Spring Cloud "约定优于配置"的风格。你唯一做的是 pom 加一个依赖 + yml 写一行地址。

### 链路 B：服务发现（你现在就能玩）

```
浏览器 GET http://localhost:8082/discovery/user-service
   → order-service 的 DiscoveryController.instances()
   → DiscoveryClient.getInstances("user-service")
   → 底层 NacosClient：先查本地缓存，没有则向 Nacos 拉取，返回实例列表
   → [{"uri":"http://192.168.x.x:8081","port":8081}]
```

**模块 03 剧透**：OpenFeign 调 user-service 时，第一步就是这条链路——先查电话簿拿到 IP，再发 HTTP。本模块你用肉眼看到它，模块 03 让它自动发生。

### 最容易出错的地方（本模块专属）

1. **9848 端口没映射**：Nacos 客户端 2.x 用 gRPC 通信，端口规则是"主端口+1000"。docker-compose 里漏了 9848 → 服务启动报 `Client not connected, current status: STARTING`；
2. **注册的 IP 不对**：多网卡/开着 VPN 的机器，服务可能把 VPN 虚拟网卡的 IP 注册上去，别人按这个 IP 调不通。可用 `spring.cloud.nacos.discovery.ip` 手动指定；
3. **Nacos 没起来服务先起了**：客户端注册失败会自动重试，但如果 Nacos 迟迟不就绪，控制台就是空的。**养成习惯：先中间件，后应用**。

## ④ 常见问题与解决思路

### Q1：服务启动报 `Client not connected, current status: STARTING`
**排查**：① `docker ps` 看 micro-nacos 在不在跑；② 9848 端口映射了没（`docker port micro-nacos`）；③ `docker logs micro-nacos` 看有没有启动报错。90% 是 9848 没放行。

### Q2：控制台 `http://localhost:8848/nacos` 打不开
**排查**：容器起没起 → 端口 8848 冲突没（`netstat -ano | findstr 8848`）→ 等 30 秒（Nacos 首次启动较慢，日志出现 "Nacos started successfully in stand alone mode" 才算就绪）。

### Q3：服务列表里看不到自己的服务 / 实例是空的
**排查**：① yml 里 `spring.cloud.nacos.discovery.server-addr` 写的 localhost:8848 对不对；② 启动日志搜 "register finished" 有没有；③ 控制台命名空间是不是 "public"（默认）；④ 刚启动有秒级延迟，刷新几次。

### Q4：注册上去的 IP 是个奇怪地址（如 192.168.x.x 不是你局域网段，或注册了虚拟网卡）
**原因**：多网卡机器 Nacos 选错了网卡。**解决**：yml 加 `spring.cloud.nacos.discovery.ip: 你真实的IPv4`（用 `ipconfig` 查）。

### Q5：Ctrl+C 停了服务，控制台里实例还在（或反过来突然没了）
**解释**：正常。停服会主动注销；如果是 kill -9 或断电，来不及注销，靠心跳超时（15 秒不健康 / 30 秒剔除）被请出电话簿。**这正是心跳机制存在的意义**——服务"暴毙"也能被自动发现。

## ⑤ 动手实践

### 第 1 步：启动 Nacos（我已写进 docker-compose.yml）
```bash
docker compose -f docker/docker-compose.yml up -d nacos
docker logs -f micro-nacos        # 看到 "started successfully" 即就绪，Ctrl+C 退出日志
```
浏览器打开 **http://localhost:8848/nacos**（账号密码 `nacos/nacos`）→ 左侧"服务管理 → 服务列表"。

### 第 2 步：IDEA 重启两个服务
先 Maven 面板刷新（pom 加了新依赖），再重启 UserApplication、OrderApplication。启动日志中找到这一行：
```
nacos registry, DEFAULT_GROUP user-service xxx.xxx.x.x:8081 register finished
```

### 第 3 步：在控制台验收
刷新 Nacos"服务列表"→ 应看到 `user-service`、`order-service` 各 1 个实例，"健康状态"绿色圆点。点服务名进去还能看实例详情（IP、端口、心跳时间）。

### 第 4 步：用接口验证"服务发现"
```bash
curl http://localhost:8082/discovery                       # → ["order-service","user-service"]
curl http://localhost:8082/discovery/user-service          # → user-service 的实例 IP:端口
```
**试着关掉 user-service 再查**——30 秒内实例消失（心跳超时被剔除），这就是电话簿的"自动销号"。

### 第 5 步：提交（由导师已完成的提交说明，供对照）
```bash
git add -A
git commit -m "feat(module-01): 集成 Nacos 注册中心"
git push origin main
```
改动清单：两个 pom 加 nacos-discovery 依赖、两个 yml 加 server-addr、新增两个 DiscoveryController、docker-compose 加 nacos 服务。

## ⑥ 学习检查（答题后结业）

**第 1 题（简答）**：order-service 查到 user-service 的实例列表后会缓存在本地。既然 Nacos 是"电话簿"，为什么要抄一份在手里？缓存会不会导致"调到已经挂掉的实例"？怎么办？

**第 2 题（简答）**：注册中心挂了（`docker stop micro-nacos`），已经互相调用的两个服务会立刻瘫痪吗？分别说"已启动的服务"和"新启动的服务"会怎样。（可以亲手实验验证你的猜想！）

**第 3 题（选择）**：微服务 A 部署了 3 个实例都注册到 Nacos。某实例被 `kill -9` 强杀，没机会主动注销。Nacos 靠什么发现它死了？
A. 管理员手动下线  B. 心跳超时（15 秒标记不健康，30 秒剔除）  C. 调用方报错后通知 Nacos  D. 永远不会剔除
