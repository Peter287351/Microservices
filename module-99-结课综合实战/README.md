# 模块 99：结课综合实战 —— 从一棵苗到一片林

> 恭喜走到这里。这不是一次普通的"模块学习"，而是**毕业典礼**：
> ①回顾最终架构 ②毕业考试（从零复现全检验）③面试弹药库 ④简历写法 ⑤进阶路线图。

---

## ① 毕业总览：你亲手建成的最终架构

```
                              浏览器 / Apifox / curl
                                      │ 唯一入口 :8080
                                      ▼
                 ┌────────────────────────────────────────┐
                 │   gateway-service (WebFlux 网关)        │
                 │  · JWT 统一验票（模块10，验签不过→401）    │
                 │  · 路由 /api/{users,orders,accounts,auth}│
                 │  · lb:// 负载均衡（模块04）               │
                 └───┬──────────────┬──────────────┬───────┘
          登录(放行)  │              │              │
                     ▼              ▼              ▼
          ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
          │ auth-service │  │ order-service│  │ user-service │
          │  :9000       │  │  :8082       │  │  :8081(×N)   │
          │ 签发 JWT     │  │ 下单(TM)     │  │ 用户/账户(RM)│
          └──────────────┘  └──┬───────┬──┘  └──┬───────────┘
                               │       │        │
             Feign 调用(带XID) │       │ MQ事件  │ 订阅订单事件
                     ┌─────────┘       ▼        ▼
                     │        ┌────────────┐ ┌────────────┐
                     │        │ RocketMQ   │ │ 用户消费    │
                     │        │ order-     │ │ (加积分/通知)│
                     │        │ created-   │ └────────────┘
                     │        │ topic      │
                     │        └────────────┘
                     ▼
          ┌──────────────────────────────────────────────┐
          │ 横切基础设施（Docker Compose 一键管理）          │
          │ · Nacos     :8848 注册中心+配置中心（模块01/02） │
          │ · MySQL     :3307 业务库 + undo_log（模块00/09）│
          │ · Sentinel  :8858 限流熔断规则（模块06）        │
          │ · Zipkin    :9411 全链路瀑布图（模块07）        │
          │ · Seata     :8091 事务协调器 TC（模块09）       │
          │ · RocketMQ  :9876/8180 消息驱动（模块08）       │
          └──────────────────────────────────────────────┘
   一条"下单"请求穿越：网关验票 → Seata 全局事务（扣款+落库）→ MQ 广播
   → Zipkin 记录全程 → Sentinel 全程护航 → Nacos 全程提供名字与配置
```

**一句话总结**：两个业务服务 + 一个网关 + 一个认证中心 + 六大中间件，
十种治理能力（注册/配置/调用/负载/路由/限流熔断/追踪/消息/事务/安全）全部到位。

## ② 毕业考试：从零复现全检验（对照 [知识点回顾与复习实操](../docs/知识点回顾与复习实操.md)）

> 检验标准：**合上所有教程，只看下面的骨架**能完整跑通。卡住的每一处，就是你要回炉的那一模块。

```bash
# ═══ 准备 ═══
docker compose -f docker/docker-compose.yml up -d        # ① 全部中间件（8个容器）
docker logs micro-nacos --tail 3                         # ② 等 nacos/z/mysql 就绪
# ③ 若数据卷是新的：手动建 Topic + 建 Nacos 配置 user-service.yml + 建 undo_log
docker exec micro-rocketmq-broker sh mqadmin updateTopic \
  -n rocketmq-namesrv:9876 -b 192.168.85.1:10911 -t order-created-topic -r 8 -w 8

mvn clean install -DskipTests                            # ④ 全量构建

# ⑤ 按序启动 5 个进程（IDEA）：User → Order → Auth → Gateway
#    （启动日志各找 register finished / Netty started）

# ═══ 全链路冒烟（每一步对应一个模块）═══
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"zhangsan","password":"123456"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
echo $TOKEN                                              # ← 模块10 登录签发

curl -H "Authorization: Bearer $TOKEN" \
     "http://localhost:8080/api/users/1"                 # ← 模块05 网关+模块01 注册发现

printf '{"userId":1,"productName":"毕业典礼纪念款","amount":99.00}' > /tmp/order.json
curl -X POST -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json; charset=utf-8" \
     --data-binary @/tmp/order.json \
     http://localhost:8080/api/orders                    # ← 模块09 全局事务+模块03 Feign 校验

curl "http://localhost:8080/api/accounts/balance?userId=1"   # ← 余额同步扣减

# user-service 控制台看 "[MQ] 收到订单创建事件"                # ← 模块08 消息驱动
# Zipkin :9411 看这条 Trace 的瀑布图                           # ← 模块07 链路追踪
printf '{"userId":1,"productName":"炸弹","amount":1}' > /tmp/bomb.json
curl -X POST -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json; charset=utf-8" \
     --data-binary @/tmp/bomb.json \
     http://localhost:8080/api/orders                    # ← Seata 全局回滚 + 余额不变
```

**通过标准**：以上 12 步一次跑通零报错。跑通了，你就毕业了。

## ③ 面试弹药库（每模块一句话精华，完整版见[考题与答案汇编](../docs/考题与答案汇编.md)）

| 模块 | 面试官会问 | 你的答案内核 |
|------|-----------|-------------|
| 00 脚手架 | 多模块怎么管版本？ | 父 pom dependencyManagement + 三 BOM；import 不继承属性覆盖 |
| 01 Nacos | 注册中心挂了服务还能调吗？ | 能——本地缓存+无限重连+操作重放（redo）；脏窗口靠重试兜底 |
| 02 Config | 配置动态刷新原理？ | Nacos 推送变更 → RefreshEvent → @RefreshScope Bean 销毁重建 |
| 03 Feign | 接口没实现类怎么调用？ | JDK 动态代理；Feign 调用没埋点会断链路（feign-micrometer 教训） |
| 04 LB | 客户端 vs 服务端负载均衡？ | 进程内直连 vs 中间人转发；实例列表来自注册中心自动推送 |
| 05 Gateway | StripPrefix？lb://？ | 剥路径前缀；lb=按服务名走 LB。WebFlux 不能混 MVC |
| 06 Sentinel | 限流和熔断的区别？ | 限流管"进多少"（QPS/线程数），熔断管"下游病了快速失败"（三态循环） |
| 07 Zipkin | 跨服务链路怎么串起来？ | TraceId 随 HTTP Header 传播；Feign 要单独埋点 |
| 08 RocketMQ | 消息丢失/重复怎么办？ | 三段各有对策；至少一次投递+消费端幂等=恰好一次 |
| 09 Seata | AT 模式原理？ | 一阶段本地提交+undo_log 镜像，二阶段提交删日志/回滚按镜像补偿 |
| 10 JWT | 无状态怎么验票？服务端真不存东西吗？ | 验签名+验 exp 即可信；无法作废是天生弱点，黑名单打补丁 |

**答不上来的**：回到对应模块 README 复习 + 重跑复习实操，不要硬背。

## ④ 简历怎么写这个项目

**项目名称**：基于 Spring Cloud Alibaba 的电商微服务系统（个人全栈实践）

**技术栈行**：
`Spring Boot 3.2 / Spring Cloud 2023 / Spring Cloud Alibaba / Nacos / OpenFeign / Gateway / Sentinel / Micrometer Tracing + Zipkin / RocketMQ / Seata / Spring Security + JWT / MySQL / Docker Compose / Maven 多模块`

**项目描述（示例句式，按需删改）**：
> 从零设计并实现电商核心域（用户/订单/认证/网关）的微服务架构，围绕"下单"主链路依次引入注册发现、配置中心、声明式调用、负载均衡、网关路由、限流熔断、链路追踪、消息驱动、分布式事务与统一安全认证共 10 项治理能力；全部中间件以 Docker Compose 编排，累计 13+ 真实故障排查复盘（文档化五段式错误知识库）。

**技术亮点（挑 4~6 条背熟，每条都能讲 5 分钟）**：
- 基于 Nacos 实现服务注册发现与配置中心，`@RefreshScope` 实现配置秒级热更新；
- OpenFeign + LoadBalancer 声明式跨服务调用，定位并修复 Feign 缺失 Micrometer 埋点导致的链路断裂问题；
- Sentinel 实现接口限流与兜底降级，修正异常包装导致的降级误报（异常因果链解包）；
- Seata AT 模式保障"下单+扣款"跨服务数据一致性，设计失败注入实验验证全局回滚；
- RocketMQ 事件驱动解耦下单与下游动作，实践"至少一次投递+消费端幂等"可靠性方案；
- Spring Security + JWT 网关统一鉴权，业务服务零安全代码；
- Micrometer Tracing + Zipkin 全链路追踪，覆盖网关/Feign/JDBC 自动埋点；
- 维护持续更新的错误知识库（20+ 条五段式复盘），覆盖环境/依赖/网络/编码类典型故障。

**避坑提示**：简历上写的每一条，都要能被追问两层——"怎么做的"（说流程）+"为什么这么做"（说取舍）。
本仓库的每个模块 README ③④节就是你被追问时的底气。

## ⑤ 毕业挑战（进阶路线，选一条走）

| 方向 | 内容 | 推荐资源起点 |
|------|------|-------------|
| A. 事务进阶 | 把 AT 改造为 TCC（Try/Confirm/Cancel 三段手写），对比吞吐差异 | Seata 官方 TCC 文档 |
| B. 观测进阶 | 换 SkyWalking（Agent 接入）看拓扑图与告警 | SkyWalking 官网快速开始 |
| C. 标准安全 | 上完整 Spring Authorization Server（授权码+客户端凭证） | Spring 官方 SAS Guides |
| D. 生产化 | 中间件上 Kubernetes / 写 CI（GitHub Actions 自动构建推送） | 官方 K8s Tutorial |
| E. 工程质量 | 给下单/扣款写单元测试+集成测试（Testcontainers） | Testcontainers 官网 |
| F. 全栈补完 | Vue3 + Element Plus 前端接网关（当初说好的选做模块 11） | Vue3 官方文档 |

## ⑥ 结课寄语

你从"Java 微服务零基础"走到今天：**13 个模块文档、5 个服务、8 个容器、
26 个 git 提交（会更多）、13 条亲手排障复盘**——每一个坑都是你自己或我们一起踩过、
查过、修过、记下来的。微服务没有"学完"，只有"遇到新问题时的底气"。
这份底气，你已经有了。

毕业不是结束。④⑤两节是送给未来你的地图。

—— 你的导师
