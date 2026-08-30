# 模块 03：OpenFeign 服务调用 —— 订单服务第一次"打电话"

> 目标：下单时 order-service **跨进程**调用 user-service 校验用户。
> 从这一刻起，我们的项目才算真正的微服务——两个服务之间存在远程通信了。

---

## ① 项目背景：那个 TODO 等到了它的大日子

还记得吗？[OrderService.java](../order-service/src/main/java/com/example/order/service/OrderService.java) 里从模块 00 起就躺着一个注释：

> TODO(模块 03 OpenFeign)：真正的电商下单必须先调 user-service 校验用户是否存在……

**为什么要校验？** 订单表里的 `userId` 目前是个"裸数字"——给 999 也能下单成功（你模块 00 亲自试过，`userId=1` 只是碰巧存在）。真实电商下单必须确认：这个用户存在、状态正常，否则订单就是孤儿数据。

**模块 01/02 的铺垫今天全部接上**：Feign 拿着"user-service"这个名字去 Nacos 查电话簿（模块 01），拿到实例地址发起调用。没有注册中心，Feign 就是个写死 IP 的 HttpClient。

## ② 概念文档：Feign = "把 HTTP 请求伪装成方法调用"

### 2.1 没有 Feign 的世界（对比理解）

用最原始的方式远程调用要写一大坨：

```java
// 手工版：查注册中心 → 选实例 → 拼 URL → 发请求 → 判状态码 → 反序列化 → 异常处理
List<ServiceInstance> instances = discoveryClient.getInstances("user-service");
ServiceInstance inst = loadBalancer.choose("user-service");
String url = inst.getUri() + "/users/" + id;
Result response = new RestTemplate().getForObject(url, Result.class);
// ...还要自己处理超时、重试、编码解码
```

Feign 的世界（[UserClient.java](../order-service/src/main/java/com/example/order/client/UserClient.java) 全部内容）：

```java
@FeignClient(name = "user-service")
public interface UserClient {
    @GetMapping("/users/{id}")
    Result<UserDTO> getById(@PathVariable("id") Long id);
}
```

**一个接口，零实现类**——这就是"声明式"：你只声明"我要调什么"，怎么发 HTTP、怎么查地址、怎么解析，框架全包。

### 2.2 它是怎么变出来的？——动态代理（考题①的答案）

启动时 `@EnableFeignClients`（[OrderApplication.java:14](../order-service/src/main/java/com/example/order/OrderApplication.java)）扫描所有 `@FeignClient` 接口，用 JDK **动态代理**给每个接口生成一个"实现类"。你注入的 `UserClient` 其实是这个代理对象：

```
userClient.getById(1)  →  代理对象拦截方法调用
  → 看方法上的注解：@GetMapping("/users/{id}")、@PathVariable("id")
  → 生成 HTTP 请求：GET /users/1，目标是"name = user-service"
  → 找注册中心：user-service 现在在哪些 IP 上活着？
  → 负载均衡挑一个实例（模块 04 的主角）
  → 发送请求 → 收到 JSON → 按返回值类型反序列化成 Result<UserDTO> → 返回
```

### 2.3 架构图：下单链路第一次跨进程

```
 POST /orders {"userId":1,...}
        │
        ▼
┌─ order-service :8082 ──────────────────────────────────────┐
│ OrderController.create()                                   │
│   └─ OrderService.create() [OrderService.java:45]          │
│       └─ validateUser() [OrderService.java:58]             │
│           └─ userClient.getById(1)  ← 动态代理             │
└───────────────┬────────────────────────────────────────────┘
                │ ①查电话簿                ②HTTP GET /users/1
                ▼                                    │
      ┌──────────────────┐                          ▼
      │ Nacos :8848      │              ┌─ user-service :8081 ────┐
      │ user-service     │              │ UserController.getById()│
      │  → 192.168.x.1:  │              │ UserService.getById()   │
      │    8081          │              │   → SELECT * FROM t_user│
      └──────────────────┘              └───────────┬─────────────┘
                                                    │ Result JSON
                                    ◄───────────────┘
                              反序列化 → 校验 code==0 → 落库 t_order
```

## ③ 链路分析：一次"下单"的完整跨服务旅程

以 `POST /orders {"userId":1,"productName":"蓝牙耳机","amount":299}` 为例：

| # | 发生什么 | 代码位置 |
|---|---------|---------|
| 1 | 参数校验通过，进入业务层 | `OrderService.java:45` `create()` |
| 2 | **先远程校验用户**（新！） | `OrderService.java:46` → `:58` `validateUser()` |
| 3 | Feign 代理发 HTTP | `UserClient.java:24` 接口方法被代理拦截 |
| 4 | 查 Nacos 拿实例、负载均衡 | Feign+LoadBalancer 内部（模块 04 展开） |
| 5 | user-service 处理：查库 | `UserController.getById:32` → `UserService.getById:27` |
| 6 | 返回 `Result<UserDTO>` JSON | Feign 反序列化回 Java 对象 |
| 7 | code==0 才放行 | `OrderService.java:60-62`，否则抛 `USER_NOT_FOUND(1001)` |
| 8 | 生成订单号落库 | `OrderService.java:48-54` |

**容易出错的地方**：
1. Feign 接口的 `@PathVariable` **必须显式写名字** `@PathVariable("id")`——接口编译后参数名丢失（还记得模块 00 的 `-parameters` 教训吗？同一族问题）；
2. **DTO 字段名必须和对方 JSON 对齐**：`Result<UserDTO>` 反序列化靠字段名匹配，写成 `userName` 就永远是 null；
3. 服务名 `@FeignClient(name = "user-service")` 拼写必须和对方 `spring.application.name` 一字不差。

## ④ 常见问题与解决思路

### Q1：启动就报错 `...FeignClient...找不到` 或注入失败
**排查**：① `@EnableFeignClients` 加了吗（[OrderApplication.java:14](../order-service/src/main/java/com/example/order/OrderApplication.java)）；② 接口上 `@FeignClient` 注解在不在；③ Feign 接口必须能被扫描到（在 `com.example.order.client` 包下，默认扫描范围之内）。

### Q2：调用时报 `No servers found for service: user-service` / `Connection refused`
**含义**：电话簿里查不到对方。**排查**：① Nacos 控制台里 user-service 是否健康在线；② `@FeignClient(name=...)` 是否写错（多空格、大小写）；③ 对方 `spring.application.name` 是什么就对什么。

### Q3：返回 404
Feign 调的路径在对方不存在。**排查**：对比两边路径——Feign 接口写的是 `GET /users/{id}`，对方 UserController 是 `@RequestMapping("/users") + @GetMapping("/{id}")`，拼起来必须一致。改了对方接口忘了同步 Feign 是常见事故。

### Q4：调通了但字段全是 null
DTO 字段名和对方 JSON 对不上。**排查**：把对方接口的真实返回贴出来逐字段比对；注意 `@JsonProperty`、命名风格（下划线 vs 驼峰）差异。

### Q5：偶发 `Read timed out`
Feign 默认连接 10s / 读取 60s，对方接口慢就会超时。本模块先知道概念，**模块 06 用 Sentinel 给调用加保护**，模块 04 也会讲到超时配置项。思考：如果 user-service 响应 3 秒，下单接口就得等 3 秒——**同步调用的代价**，模块 08 的 MQ 异步就是解药之一。

## ⑤ 动手实践（10 分钟）

### 第 1 步：重启 order-service
IDEA：Maven 刷新 → 重启 OrderApplication（user-service 不用动）。启动日志无红色即成功。

### 第 2 步：见证"远程校验"
```bash
# ① 用真实存在的用户下单（userId=1 之前创建过 zhangsan）→ 成功
printf '{"userId":1,"productName":"无线鼠标","amount":89.90}' > /tmp/order.json
curl -X POST http://localhost:8082/orders -H "Content-Type: application/json; charset=utf-8" --data-binary @/tmp/order.json

# ② 用不存在的用户下单（userId=999）→ 被远程校验拦截！
printf '{"userId":999,"productName":"赢单","amount":1.00}' > /tmp/order_bad.json
curl -X POST http://localhost:8082/orders -H "Content-Type: application/json; charset=utf-8" --data-binary @/tmp/order_bad.json
# 预期：{"code":1001,"message":"用户不存在"} ← 这个 1001 是 user-service 查完库告诉它的！
```
对比模块 00：当时 `userId=999` 也能下单成功（没有校验），现在被拦了。

### 第 3 步：更狠的实验——把 user-service 整个停掉再下单
预期：下单失败（Feign 连不上目标）。**记住这个"脆"的感觉**：同步调用把两个服务的可用性绑在一起了——这正是模块 06 熔断降级要解决的问题。实验完记得重启 user-service。

### 第 4 步：提交（导师已完成，供对照）
```bash
git commit -m "feat(module-03): 集成 OpenFeign，下单远程校验用户"
```
改动：order-service pom 加 openfeign、启动类加 @EnableFeignClients、
新增 client/UserClient + dto/UserDTO、OrderService 下单前远程校验用户。

## ⑥ 学习检查

**第 1 题（简答）**：`UserClient` 是个接口，工程里没有任何实现类，为什么 `userClient.getById(1)` 能真的发出 HTTP 请求？这套机制叫什么？

**第 2 题（简答）**：第 3 步实验中 user-service 停机后下单失败。请说出：① 现在的报错行为是"快速失败"还是"长时间卡死后失败"？② 这种"一个服务挂、下单跟着挂"的现象叫什么？你猜想有哪些解决方向（下两节课揭晓）？

**第 3 题（简答）**：为什么 order-service 里要新建 `UserDTO`，而不是直接把 user-service 的 `User` 实体类复制过来用，或者干脆让 user-service 把实体类打成 jar 包给 order-service 引用？
