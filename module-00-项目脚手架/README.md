# 模块 00：项目脚手架 —— 万事开头难，开头搭好了后面就不难

> 本模块目标：**不引入任何 Spring Cloud 组件**，先把"两个独立 Spring Boot 应用 + 一个 MySQL"的地基打好。
> 后面 10 个模块都是在这块地基上添砖加瓦。

---

## ① 项目背景：我们在做什么、为什么这么开始

想象你是电商公司的架构师。公司有个跑了好几年的单体应用，代码几十万行，改一行要全量回归测试，
发布一次全公司加班。老板决定：拆微服务。你拿到两个最核心的业务域——**用户**和**订单**。

我们现在的处境一模一样，但更彻底：从零开始。

- **user-service（8081）**：用户注册、查询。暴露 `GET/POST /users` 接口。
- **order-service（8082）**：下单、查询订单。暴露 `GET/POST /orders` 接口。
- 两个服务**各自独立进程、独立打包、独立启动**，此刻互不通信——这就是"分布式部署的单体"。
- 从模块 01 起，我们逐个引入 Nacos → OpenFeign → Gateway → Sentinel → … 把它们变成真正的微服务。

**为什么先搭脚手架而不是一上来就上微服务组件？**
地基不稳，楼盖得越高塌得越快。先确保：Maven 多模块工程能编译、服务能连数据库、接口能冒烟通过、
git 仓库能管版本。每一步都验证过，后面出问题时才能确定"新引入的组件"才是嫌疑人。

## ② 概念文档：本模块涉及的四个核心概念

### 2.1 单体 vs 微服务（先建立坐标系）

```
【单体应用】                      【微服务】
┌───────────────────┐            ┌──────────┐   ┌──────────┐
│  用户模块          │            │ user-    │   │ order-   │
│  订单模块          │   ==拆==>  │ service  │   │ service  │
│  库存模块          │            │ :8081    │   │ :8082    │
│  一个进程一个库     │            └────┬─────┘   └────┬─────┘
└───────────────────┘                 │              │
                                   ┌──┴──────────────┴──┐
                                   │   MySQL (Docker)   │
                                   └────────────────────┘
```

| 维度 | 单体 | 微服务 |
|------|------|--------|
| 部署 | 一个 jar 全量发布 | 每个服务独立发布、独立扩容 |
| 故障影响 | 一处崩全部崩 | 订单崩了用户还能登录 |
| 技术栈 | 统一 | 各服务自选（但本项目统一） |
| 代价 | 随规模变卡 | **分布式复杂度**（网络会挂、数据会不一致）→ 这正是后面 10 个模块要解决的 |

### 2.2 Maven 多模块工程：为什么需要"父工程"

三个 pom 构成树：`pom.xml`（父）→ `common` / `user-service` / `order-service`（子）。

- 父 pom 的 `<packaging>pom</packaging>` 表示它只做管理不产 jar；
- `<modules>` 列出子模块，根目录一条 `mvn package` 就能按依赖顺序构建全部；
- 父 pom 的 `<dependencyManagement>` **锁定版本**：Spring Boot 3.2.4 / Spring Cloud 2023.0.1 /
  Spring Cloud Alibaba 2023.0.1.0 是官方公布的兼容组合。子模块引依赖**不写版本号**，
  全部从这里继承——从根源杜绝"版本冲突"（微服务第一大坑）。
- 版本冲突为什么可怕：A 依赖要用 X 库 2.0，B 依赖要用 X 库 1.0，Maven 只会留一个，
  另一个运行时直接 `NoSuchMethodError`。

### 2.3 依赖管理的"三个 BOM"

BOM（Bill of Materials，物料清单）= 一份"推荐版本对照表"。本项目父 pom 导入了三张表：

```
spring-boot-dependencies          → 管理 Spring 全家桶 + 常用第三方库版本
spring-cloud-dependencies         → 管理 Spring Cloud 各组件版本
spring-cloud-alibaba-dependencies → 管理 Nacos/Sentinel/Seata 客户端版本
```

### 2.4 服务的分层结构（每个服务内部都长这样）

```
user-service/
├── controller/   接口层：接 HTTP 请求、参数校验、包装 Result 返回
├── service/      业务层：业务规则（用户名不能重复等）
├── repository/   数据层：继承 JpaRepository，方法名即 SQL
├── entity/       实体：POJO + JPA 注解 = 数据库表
└── dto/          数据传输对象：接口出入参（和 entity 隔离，防止表结构泄露到接口）
```

**common 公共模块**：统一返回 `Result{code,message,data}`、错误码 `ErrorCode`、业务异常、
全局异常处理器。所有服务共享，保证全系统返回结构一致。

## ③ 链路分析：一次"下单"请求的完整流转（模块 00 单体版）

以 `POST /orders` 请求体 `{"userId":1,"productName":"机械键盘","amount":199.00}` 为例：

```
curl/Postman
   │ HTTP POST (JSON)
   ▼
┌─ order-service 进程 (8082) ──────────────────────────────┐
│ 1. Tomcat 接收请求                                        │
│ 2. OrderController.create()          ← 接口层             │
│    · @Valid 触发参数校验(金额>0)                           │
│ 3. OrderService.create()             ← 业务层             │
│    · generateOrderNo() 生成订单号                          │
│    · future: 这里将远程调用 user-service(模块03)           │
│ 4. OrderRepository.save()            ← 数据层             │
│    · Hibernate 生成 INSERT INTO t_order                   │
└──────────────┬───────────────────────────────────────────┘
               │ JDBC (TCP 3307)
               ▼
        MySQL 容器 micro 库 t_order 表（一行数据落盘）
               │
               ▼
响应一路返回：Order 实体 → Jackson 序列化为 JSON → Result 包装
{"code":0,"message":"success","data":{...}}
```

**关键代码调用点**（可点击跳转）：

| 步骤 | 位置 | 说明 |
|------|------|------|
| 1 | `order-service/.../controller/OrderController.java:34` | `create()`：`@Valid` 校验 → 调 service → `Result.ok()` 包装 |
| 2 | `order-service/.../service/OrderService.java:39` | `create()`：生成订单号、组装实体、落库；`TODO` 注释标记了模块 03 的接入点 |
| 3 | `order-service/.../service/OrderService.java:50` | `generateOrderNo()`：时间戳+随机数 |
| 4 | `order-service/.../repository/OrderRepository.java:10` | 方法名 `findByUserIdOrderByCreatedAtDesc` → Spring Data 自动翻译成 SQL |
| 5 | `order-service/.../entity/Order.java:25` | 实体注解 → 自动建表（详见 [docs/数据库设计说明.md](../docs/数据库设计说明.md)） |

**异常路径**：任何一层抛出 `BusinessException`（如订单不存在）→ `common` 模块的
`GlobalExceptionHandler` 统一捕获 → 返回 `{"code":2001,...}`，Controller 里零 try-catch。

**本模块最容易出错的地方**（也是我们今天真实踩过的，见下一节）：
端口被占、jar 打包方式、编译参数、中文编码——全是**环境类**问题，不是业务逻辑问题。

## ④ 常见问题与解决思路（全部来自今天的真实踩坑！）

### 问题 1：容器启动失败 —— `ports are not available: bind: 0.0.0.0:3306`
**现象**：`docker compose up` 时 MySQL 容器起不来。
**原因**：宿主机 3306 已被本机早已安装的 Windows MySQL 服务（mysqld.exe）占用。
**排查**：`netstat -ano | findstr 3306` → 拿到 PID → `tasklist /FI "PID eq xxx"` 看是谁。
**解决**：容器端口映射改为 `3307:3306`（宿主机 3307 → 容器 3306），JDBC URL 同步改成 3307。
**经验**：Docker 端口映射 = "宿主机端口:容器端口"，宿主机侧冲突就换一个，容器内部不受影响。

### 问题 2：`java -jar` 报"没有主清单属性"
**现象**：jar 打出来了，`java -jar` 却报 `no main manifest attribute`。
**原因**：我们没有继承 `spring-boot-starter-parent`，`spring-boot-maven-plugin` 的
`repackage` 目标不会自动执行，打出来的是普通瘦 jar（几百 KB），不是可执行 fat-jar（48MB）。
**解决**：父 pom 插件管理中显式声明 `<executions><execution><goals><goal>repackage</goal>`。
**经验**：自己写父工程时，官方 parent 默默帮你做的事要自己补。

### 问题 3：接口报 500 —— `parameter name information not available via reflection`
**现象**：`GET /users/1` 返回 `{"code":500}`，日志里 `Ensure that the compiler uses the '-parameters' flag`。
**原因**：`@PathVariable Long id` 没写名字，Spring 需要反射读参数名；JDK 21 编译默认丢弃参数名。
**解决**：父 pom 加 `<maven.compiler.parameters>true</maven.compiler.parameters>`。
**经验**：Spring Framework 6.1 起移除了参数名兜底机制，这是老项目升级 Boot 3.2 的高频坑。

### 问题 4：Windows curl 发中文 JSON 报 `Invalid UTF-8 start byte 0xbb`
**现象**：下单接口 500，日志 `JSON parse error`；但纯英文请求正常。
**原因**：Windows 控制台默认 GBK 编码，命令行里的"机械键盘"以 GBK 字节发出，服务端按 UTF-8 解析失败。
**解决**：中文 JSON 先用 UTF-8 写入文件，`curl --data-binary @order.json` 发送；
或统一用 Postman/Apifox 测试。
**经验**：`0xbb` 正是"机"字 GBK 编码的首字节——学会从报错字节反推编码问题。

### 问题 5：缺请求参数 / 校验失败返回 500 而非 400
**现象**：`GET /orders` 不带 `userId` 返回 `{"code":500,"message":"系统内部错误"}`。
**原因**：Spring 抛的 `MissingServletRequestParameterException` 落入了兜底 `Exception` 处理器。
**解决**：`GlobalExceptionHandler` 里为它和 `HttpMessageNotReadableException` 单独加
`@ExceptionHandler`，精确返回 400 + 明确提示。
**经验**：500 应该留给"未知异常"，可预见的客户端错误一律 4xx，这是排障时的重要信号。

### 问题 6（预防）：服务连不上数据库 `Communications link failure`
**排查三连**：① `docker ps` 看 micro-mysql 是否 healthy；② JDBC URL 端口是否 3307（不是 3306）；
③ 密码是否与 docker-compose.yml 的 `MYSQL_ROOT_PASSWORD` 一致。

## ⑤ 动手实践：从零到冒烟通过（跟着敲一遍）

> 假设你换了一台电脑从头来，或者想验证自己能独立复现。

### 第 1 步：启动 MySQL
```bash
# 先启动 Docker Desktop，然后在仓库根目录：
docker compose -f docker/docker-compose.yml up -d
# 等待健康检查（约 30 秒）
docker inspect --format '{{.State.Health.Status}}' micro-mysql   # 输出 healthy 即可
```

### 第 2 步：构建
```bash
mvn clean install -DskipTests
# 看到 BUILD SUCCESS；首次构建会从阿里云镜像下载依赖
```

### 第 3 步：启动两个服务（开两个终端）
```bash
# 终端 1
java -jar user-service/target/user-service-1.0.0.jar
# 终端 2
java -jar order-service/target/order-service-1.0.0.jar
# 也可以用 IDEA 直接运行两个 Application 主类
```

### 第 4 步：冒烟测试
```bash
# 建用户
curl -X POST http://localhost:8081/users -H "Content-Type: application/json" \
     -d '{"username":"zhangsan","email":"zhangsan@example.com"}'
# 查用户（把 1 换成上一步返回的 id）
curl http://localhost:8081/users/1
# 下单（含中文时用 UTF-8 文件方式，见问题 4）
printf '{"userId":1,"productName":"机械键盘","amount":199.00}' > /tmp/order.json
curl -X POST http://localhost:8082/orders -H "Content-Type: application/json; charset=utf-8" \
     --data-binary @/tmp/order.json
# 查订单
curl http://localhost:8082/orders/1
# 观察异常路径
curl http://localhost:8081/users/999        # → code 1001 用户不存在
curl http://localhost:8082/orders           # → code 400 缺少必填参数
```

### 第 5 步：提交到 GitHub

**(a) 首次：创建远程仓库 + 配置 SSH（只需做一次）**

1. 登录 [github.com](https://github.com) → 右上角 `+` → `New repository`；
2. Repository name 填 `Microservices`，选 **Private**，**不要**勾选任何初始化文件（README/.gitignore 都不要，本地已有）→ `Create repository`；
3. 本机生成 SSH 密钥并添加到 GitHub：
   ```bash
   ssh-keygen -t ed25519 -C "你的邮箱@example.com"   # 换成你自己的邮箱；一路回车即可
   cat ~/.ssh/id_ed25519.pub                       # 复制输出的整行公钥
   ```
   GitHub 网页：`Settings → SSH and GPG keys → New SSH key` → 粘贴公钥 → `Add`；
4. 验证连通：
   ```bash
   ssh -T git@github.com     # 首次会问是否信任，输 yes；看到 "Hi xxx!" 即成功
   ```

**(b) 关联远程并推送**
```bash
git remote add origin git@github.com:你的用户名/Microservices.git
git push -u origin main        # -u 首次推送并建立跟踪关系，以后只需 git push
```

**(c) 本模块的提交（我已经在本地完成，你推送即可看到）**
```bash
git add -A
git commit -m "feat(module-00): 搭建项目脚手架（父工程+common+双服务+MySQL容器）"
git push origin main
```

**提交信息规范**：`type(module-XX): 描述`，type 取 docs/feat/fix/chore。

## ⑥ 学习检查（把答案发给导师，批改后进入模块 01）

**第 1 题（简答）**：父 pom 的 `<dependencyManagement>` 和直接在子模块 `<dependencies>` 里写依赖，有什么区别？
为什么第三方组件版本号要集中写在父 pom？

**第 2 题（简答）**：`t_order` 表里有自增主键 `id`，为什么还要单独设计一个唯一的 `order_no` 字段？
`amount` 字段为什么用 `decimal(10,2)` 而不是 `double`？

**第 3 题（选择+说明）**：当前 user-service 和 order-service **共用** micro 库（表不同）。
以下说法正确的是：
A. 这就是标准的微服务架构，不用改
B. 真实微服务应该"一个服务一个库"，共库的它们本质上是"分布式部署的单体"；拆库后跨服务的数据一致性需要专门组件（后面模块 09 的 Seata）解决
C. 应该把两张表合成一张宽表，性能最好
D. 微服务就是多个服务连同一个库

---
**做完以上实践 + 答完题，模块 00 结课。** 复习材料：[docs/数据库设计说明.md](../docs/数据库设计说明.md)。
