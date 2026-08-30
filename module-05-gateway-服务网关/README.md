# 模块 05：Gateway 服务网关 —— 给微服务王国开一扇统一的大门

> 目标：新增第三个服务 gateway-service（8080）。从此浏览器/Apifox **只访问 8080**，
> 由网关按路径规则把请求转发给 user-service / order-service。
> 模块 06 的限流、模块 10 的统一鉴权，都架在这扇门上。

---

## ① 项目背景：直连时代的三宗罪

到目前为止，调用方（浏览器/Apifox）直接连各个服务：

- 查用户 → `8081`，下单 → `8082`……服务一多，调用方要维护一张"端口地图"；
- 想给所有接口加鉴权、限流、日志？得改 N 个服务；
- 前端跨域要每个服务各配一遍。

**网关 = 唯一入口 + 统一加工厂**：所有外部请求先到 8080，网关按规则转发到内部服务。
内部服务从此可以藏在"内网"里不对外暴露。调用方只需要认识一扇门。

## ② 概念文档：Route / Predicate / Filter 三件套

### 2.1 一条路由的解剖（我们 yml 里的真实配置）

```yaml
- id: user-service-route          # ① 路由的名字（唯一即可）
  uri: lb://user-service          # ② 去哪：lb:// = 去注册中心找这个服务并负载均衡
  predicates:                     # ③ 断言：满足什么条件才算"这条路由管"
    - Path=/api/users/**
  filters:                        # ④ 过滤器：转发前/后做什么加工
    - StripPrefix=1
```

| 组件 | 类比 | 常用种类 |
|------|------|---------|
| Route 路由 | 快递分拣线上的一条传送带 | —— |
| Predicate 断言 | 分拣规则："这箱算谁的" | Path、Method、Header、Query、Time（限时活动路由） |
| Filter 过滤器 | 分拣时的加工：贴标、拆包、安检 | StripPrefix、AddRequestHeader、前缀路径、限流（模块 06）、鉴权（模块 10） |

过滤器分**前置（pre）**和**后置（post）**两段：StripPrefix 在转发前改路径（前置）；
后置可以改响应、加响应头。断言和过滤器可以叠加多条。

### 2.2 架构图：统一入口后的全景

```
 浏览器/Apifox
      │ 只认识一扇门
      ▼
┌──────────────────┐  ①断言匹配 /api/users/**   ┌─ user-service :8081 ─┐
│ gateway-service  │ ─────────────────────────► │                      │
│ :8080            │  ②StripPrefix 剥掉 /api     └──────────────────────┘
│                  │
│                  │  ③/api/orders/** → lb://order-service
│                  │ ─────────────────────────► ┌─ order-service :8082 ─┐
└──────────────────┘                            └───────────────────────┘
      │
      └─ 网关自己也注册进 Nacos；转发时用 LoadBalancer 从实例列表挑一个（模块04复用）
```

### 2.3 `lb://` 是什么？

`lb://user-service` = "这个目标不是固定地址，而是一个**服务名**"。网关交给 LoadBalancer
从 Nacos 拉实例列表挑一个（**模块 04 的客户端负载均衡在这里原样复用**）。
对比：`uri: http://localhost:8081` 是写死地址（学习可用，生产禁用——那还要注册中心干嘛）。

### 2.4 在微服务版图中的位置

```
外部 → 【网关】→ 内部各服务
        ↑ 模块06：限流熔断挂在网关路由上
        ↑ 模块10：JWT 统一鉴权过滤器（登录校验只写一处）
```

网关是"横切关注点"（鉴权/限流/日志/跨域）的集中地——这些逻辑写一次，所有服务受益。

## ③ 链路分析：`GET http://localhost:8080/api/users/1` 的完整旅程

| # | 步骤 | 说明 |
|---|------|------|
| 1 | 请求到达 8080 | Gateway（Netty/WebFlux，响应式非阻塞） |
| 2 | 断言匹配 | 逐条路由试 predicates：`/api/users/1` 命中 `Path=/api/users/**`（user 路由） |
| 3 | 前置过滤器 | `StripPrefix=1`：路径重写为 `/users/1` |
| 4 | lb:// 解析 | LoadBalancer 从 Nacos 缓存挑一个 user-service 实例（如 192.168.85.1:8081） |
| 5 | 转发 | 网关发 `GET http://192.168.85.1:8081/users/1`（响应式非阻塞转发） |
| 6 | 后置过滤 + 回传 | 响应原路返回调用方，感觉就像直连一样 |

**容易出错的地方**：
1. **依赖冲突**：Gateway 基于 WebFlux，classpath 里混入 `spring-boot-starter-web`（MVC）直接启动失败——这就是为什么 gateway-service **不能依赖 common**（common 传递携带 web，见 [gateway-service/pom.xml](../gateway-service/pom.xml) 顶部注释）；
2. **StripPrefix 算错**：`/api/users/1` 剥 1 段 = `/users/1` ✔；如果路由写成 `/users/**`（无 /api），就要配 `StripPrefix=0` 或不配；
3. **路由顺序**：多条路由都命中时，按配置顺序取第一条——精确的规则要放在前面。

## ④ 常见问题与解决思路

### Q1：启动报 `Spring MVC found on classpath, which is incompatible with Spring Cloud Gateway`
**原因**：classpath 同时有 spring-webmvc（MVC）和 gateway（WebFlux），水火不容。
**解决**：检查网关模块的依赖树（`mvn dependency:tree`），移除 web/common 相关依赖。我们设计时已规避。

### Q2：走网关 404，直连服务正常
**排查**：① 断言 Path 和请求路径对不对（`/api/users/1` vs `/users/1`）；② StripPrefix 数字对不对——最常见错误：剥完后服务端收到的是 `/api/users/1` 而服务只有 `/users/1`；③ 路由 id 重复或 yml 缩进错误导致路由根本没加载（启动日志能看到路由列表）。

### Q3：走网关 503 Service Unavailable
`lb://` 找不到实例。排查：下游服务注册了吗（Nacos 控制台）、网关引没引 nacos-discovery + loadbalancer、服务名拼写。

### Q4：请求过了网关但下游报参数校验错
StripPrefix 剥错段数导致路径对了但 query/参数丢了？其实 StripPrefix 只动路径不动参数；
若报 400 先比对"直连该路径+参数"是否正常，隔离是网关问题还是服务问题。

### Q5：跨域（CORS）报错
浏览器跨域请求被浏览器拦截。网关统一配置：
`spring.cloud.gateway.globalcors` 配置允许的来源/方法/头。等模块 10 前后做前端联调时再细讲。

## ⑤ 动手实践（15 分钟）

### 第 1 步：确认基础服务在线
Nacos 控制台确认 user-service、order-service 健康在线（不在就 IDEA 重启）。

### 第 2 步：启动网关
IDEA：Maven 刷新（父 pom 多了 gateway 模块）→ 运行 `GatewayApplication`。
看到 `Tomcat started on port 8080`（Netty，不是 Tomcat，日志会显示 Netty started / NettyWebServer）即成功。

### 第 3 步：对比"直连 vs 走网关"
```bash
# 旧世界：直连各服务
curl "http://localhost:8082/orders?userId=1"

# 新世界：全部走 8080 大门
curl "http://localhost:8080/api/orders?userId=1"          # → 订单列表（和上面返回一致！）
curl http://localhost:8080/api/users/1                    # → zhangsan
curl http://localhost:8080/api/users/welcome              # → 配置中心的 v2 欢迎语（模块02还活着）
curl http://localhost:8080/api/orders/lb-test             # → 负载均衡实验照样能用（模块04复用）
```
**验证本质**：走网关和直连返回完全相同——网关只是透明转发，不改变业务语义。

### 第 4 步：控制台观察网关日志
Gateway 日志能看到转发详情（默认日志较少，属正常；模块 07 加链路追踪后转发路径一目了然）。

### 第 5 步：提交（导师已完成，供对照）
```bash
git commit -m "feat(module-05): 新增网关服务（统一入口 8080 + 路由转发）"
```
改动：父 pom 加 gateway-service 模块、新增 gateway-service（pom/启动类/yml 路由配置）。

## ⑥ 学习检查

**第 1 题（简答）**：`StripPrefix=1` 是什么意思？如果路由断言改成 `Path=/users/**`（不带 /api 前缀），StripPrefix 应该怎么配，转发到服务的路径是什么？

**第 2 题（简答）**：`uri: lb://user-service` 里的 `lb://` 是什么协议？它把网关和前面哪个模块的知识串起来了？

**第 3 题（简答）**：为什么 gateway-service 不能依赖 common 模块？如果强行依赖了，启动时会报什么错？（提示：两套 Web 技术栈的名字）
