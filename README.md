# 电商微服务学习项目（Microservices）

> 一个从零开始的 Java 微服务学习仓库：以"电商系统"为业务背景，把两个 Spring Boot 单体服务
> 逐步改造为完整的微服务架构，覆盖注册中心、配置中心、服务调用、网关、熔断、链路追踪、
> 消息驱动、分布式事务、安全认证九大治理能力。

---

## 一、项目背景与业务设定

我们构建一个**极简电商系统**，目前只有两个核心业务：

- **user-service（用户服务，端口 8081）**：用户注册、用户信息查询
- **order-service（订单服务，端口 8082）**：下单、订单查询

核心业务场景是**"用户下单"**：

```
用户 → 下单(order-service) → 校验用户(user-service) → 创建订单
```

随着学习模块推进，这条链路会不断进化：

| 阶段 | 下单链路的样子 |
|------|---------------|
| 模块 00 | 两个独立 Spring Boot 应用，订单服务自己记账，互不通信 |
| 模块 01~02 | 两个服务注册进 Nacos，配置托管到 Nacos Config |
| 模块 03~04 | 下单时通过 OpenFeign 远程调用用户服务校验用户，LoadBalancer 均衡流量 |
| 模块 05~06 | 所有请求从 Gateway 网关进入，Sentinel 保护下单接口 |
| 模块 07 | Zipkin 上能看到下单的完整调用链 |
| 模块 08 | 下单成功后发 RocketMQ 消息，异步通知下游 |
| 模块 09 | 下单 + 扣库存跨服务，Seata 保证要么都成功要么都回滚 |
| 模块 10 | 网关统一用 JWT 鉴权，登录令牌由 auth-service 签发 |

## 二、技术栈（已确认版本，官方兼容组合）

| 类别 | 选型 | 版本 |
|------|------|------|
| 语言 / JDK | Java | 编译目标 17（本机 JDK 21 可编译运行） |
| 基础框架 | Spring Boot | 3.2.4 |
| 微服务框架 | Spring Cloud | 2023.0.1 |
| 阿里系组件 | Spring Cloud Alibaba | 2023.0.1.0 |
| 注册/配置中心 | Nacos | 2.3.2（Docker） |
| 服务调用 | OpenFeign + Spring Cloud LoadBalancer | 随 Spring Cloud |
| 网关 | Spring Cloud Gateway | 随 Spring Cloud |
| 熔断限流 | Sentinel | 1.8.x（Docker 控制台） |
| 链路追踪 | Micrometer Tracing + Zipkin | 3.x（Docker） |
| 消息队列 | Spring Cloud Stream + RocketMQ | 5.x（Docker） |
| 分布式事务 | Seata（AT 模式） | 2.0（Docker） |
| 安全认证 | Spring Security + Spring Authorization Server + JWT | 随 Spring Boot |
| 数据库 | MySQL | 8.0（Docker） |
| 构建 | Maven | 3.9.6 |

## 三、学习路线图（共 12 站，每完成一站 git 提交一次）

> 进度打卡：完成一个模块就把 `[ ]` 改成 `[x]`。

- [x] **module-00-项目脚手架** —— Maven 父工程、common 公共模块、两个单体服务、MySQL 容器、GitHub 建仓
- [x] **module-01-nacos-注册中心** —— 服务注册与发现，服务列表可视化
- [x] **module-02-nacos-config-配置中心** —— 配置托管与动态刷新
- [x] **module-03-openfeign-服务调用** —— 下单时远程校验用户
- [x] **module-04-loadbalancer-负载均衡** —— 双实例演示轮询与权重
- [x] **module-05-gateway-服务网关** —— 统一入口 8080，路由与过滤器
- [ ] **module-06-sentinel-熔断降级** —— 限流、熔断、fallback 降级
- [ ] **module-07-zipkin-链路追踪** —— 全链路 Trace 可视化
- [ ] **module-08-stream-rocketmq-消息驱动** —— 下单事件异步解耦
- [ ] **module-09-seata-分布式事务** —— 跨服务数据一致性
- [ ] **module-10-security-jwt-安全认证** —— 授权服务器 + 网关统一鉴权
- [ ] **module-99-结课综合实战** —— 全链路串联复盘（网关→鉴权→下单→Feign→Seata→MQ）

## 四、每个模块怎么学（固定节奏）

1. **讲解**：导师编写该模块 `README.md`，包含固定 6 部分
   ① 项目背景（在本系统中的定位） ② 概念文档（通俗讲解 + 文字架构图）
   ③ 链路分析（"下单"请求如何流经该模块、易错点） ④ 常见问题与排查思路（3~5 个）
   ⑤ 动手实践（集成步骤 + demo 验证 + git 提交命令） ⑥ 学习检查（3 道题）
2. **动手**：你跟着实践文档操作，跑通 demo
3. **答题**：把 3 道检查题的答案发给导师
4. **批改**：导师批改、给后续学习建议
5. **沉淀**：把该模块考题与答案写入 [docs/考题与答案汇编.md](docs/考题与答案汇编.md)（2026-08-31 起为固定流程，写完才进入下一模块）
6. **提交**：git 提交 + 推送 GitHub，本模块结课，进入下一站

## 五、目录结构（最终形态）

```
Microservices/
├── pom.xml                      # Maven 父工程：统一管版本（3 个 BOM）
├── README.md                    # 本文件：总学习计划
├── docker/
│   ├── docker-compose.yml       # 中间件容器（随模块逐步追加）
│   └── mysql/init/              # MySQL 初始化脚本
├── common/                      # 公共模块：统一返回 Result / 全局异常
├── user-service/                # 用户服务 (8081)
├── order-service/               # 订单服务 (8082)
├── gateway-service/             # 网关 (8080)        —— 模块 05 新增
├── auth-service/                # 授权服务 (9000)    —— 模块 10 新增
├── module-00-项目脚手架/
│   └── README.md                # 每个模块一个文件夹，内含教程文档
├── module-01-nacos-注册中心/
├── ...
└── module-99-结课综合实战/
```

## 六、端口与中间件规划

| 组件 | 端口 | 说明 |
|------|------|------|
| gateway-service | 8080 | 统一入口（模块 05 上线） |
| user-service | 8081 | 用户服务 |
| order-service | 8082 | 订单服务 |
| auth-service | 9000 | 授权服务器（模块 10 上线） |
| MySQL（Docker） | 3307 | 业务数据库 `micro`；本机 3306 已被 Windows 自带 MySQL 占用，故映射到 3307 |
| Nacos | 8848 / 9848 | 控制台 / gRPC（模块 01） |
| Sentinel 控制台 | 8858 | 限流规则配置（模块 06） |
| Zipkin | 9411 | 链路查询 UI（模块 07） |
| RocketMQ NameServer | 9876 | 消息队列（模块 08） |
| RocketMQ Dashboard | 8180 | MQ 控制台（模块 08） |
| Seata Server | 8091 | 事务协调器（模块 09） |

## 七、环境速查（本机实际情况）

| 工具 | 位置/版本 | 备注 |
|------|-----------|------|
| JDK | 21.0.7 `C:\Program Files\Java\jdk-21` | 编译目标 17 |
| Maven | `C:\maven\apache-maven-3.9.6` | 已配阿里云镜像；Git Bash 中若 `mvn` 不可用，见下 |
| Git | 2.51.1 | user.name=beach |
| Docker | 29.4.3 | 启动学习前先打开 Docker Desktop |

**Git Bash 里让 mvn 生效**（Windows PATH 的 `%MAVEN_HOME%\bin` 未展开导致）：

```bash
echo 'export PATH="/c/maven/apache-maven-3.9.6/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc
mvn -version   # 验证
```

## 八、常用命令速查

```bash
# ── 中间件（在仓库根目录执行）──
docker compose -f docker/docker-compose.yml up -d      # 启动已定义的所有中间件
docker compose -f docker/docker-compose.yml ps         # 查看状态
docker compose -f docker/docker-compose.yml logs mysql # 看某容器日志

# ── 构建 ──
mvn clean package -DskipTests        # 全量编译打包（首次会下载依赖）
mvn clean install -DskipTests        # 编译并安装到本地仓库（common 被其他模块引用时需要）

# ── 启动单个服务（打包后）──
java -jar user-service/target/user-service-1.0.0.jar
java -jar order-service/target/order-service-1.0.0.jar

# ── 冒烟测试 ──
curl http://localhost:8081/users/1
curl -X POST http://localhost:8082/orders -H "Content-Type: application/json" \
     -d '{"userId":1,"productName":"键盘","amount":199.00}'
```

## 九、Git 提交规范

每个模块结课时提交一次，提交信息格式：

```
<type>(module-XX): 中文描述
```

- `docs:`  仅文档变更（如本计划的首次提交）
- `feat:`  新增模块/功能（如 `feat(module-01): 集成 Nacos 注册中心`）
- `fix:`   修复问题
- `chore:` 构建/依赖调整

推送：`git push origin main`。

## 十、学习建议（写给未来的自己）

- **不要只看不动手**：每个 demo 必须亲手跑通，报错自己先查，查 20 分钟无果再问导师
- **关注"为什么"**：每引入一个中间件，先想清楚"单体解决不了什么问题"
- **保护现场**：出问题时先看容器状态 `docker ps`、服务日志，学会读堆栈是微服务的基本功
