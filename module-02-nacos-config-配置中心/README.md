# 模块 02：Nacos Config 配置中心 —— 让配置"活"起来

> 痛点：现在数据库密码、端口全写死在 `application.yml` 里，**打进 jar 包就改不了了**——
> 改个密码 = 改代码 → 重新打包 → 逐个重启。服务一多，配置管理就是灾难。
> 本模块把配置搬到 Nacos：**改配置不重启，30 秒内全网生效。**

---

## ① 项目背景：配置是微服务里最"善变"的东西

回想我们的工程：user-service 的数据库地址、密码写在 `application.yml`，随 jar 发布。三个致命问题：
1. **改一次发一次版**：生产改密码要重新打包部署，服务中断；
2. **多环境地狱**：开发连 3307、测试连内网库、生产连云库——同一个 jar 三套配置，全靠手工切换；
3. **安全裸奔**：密码躺在代码仓库里，所有有仓库权限的人都能看到生产密码。

配置中心的思路：**配置从代码里剥离，集中存放在 Nacos，服务启动时来取、运行中盯着变化**。
本模块先解决"动态刷新"，多环境和加密属于进阶话题（README ④⑤ 会点到）。

## ② 概念文档：Nacos Config 的三个核心概念

### 2.1 一份配置在 Nacos 里怎么定位？—— Namespace / Group / Data ID

| 层级 | 类比 | 本项目用法 |
|------|------|-----------|
| Namespace 命名空间 | 图书馆的楼（隔离不同环境） | 默认 `public`（学习不细分） |
| Group 分组 | 楼里的书架 | `DEFAULT_GROUP`（默认） |
| **Data ID** | **书的编号** | **`user-service.yml`**（与服务同名，约定） |

### 2.2 配置怎么"流"到服务里？（架构图）

```
【启动时·拉】
Spring Boot 启动
  → 读到 spring.config.import: optional:nacos:user-service.yml
  → NacosConfigDataLoader 向 Nacos 发 HTTP 请求拉取该 Data ID 的内容
  → 合并进 Spring 的 Environment（⚠️ 远程配置优先级高于本地 application.yml）
  → Bean 创建，@Value("${user.welcome-msg}") 从 Environment 取值注入

【运行期·推】
你在 Nacos 控制台改了 user-service.yml 并保存
  → 客户端监听到变更（2.x 走 gRPC 长连接推送，1.x 是长轮询）
  → NacosContextRefresher 发布 RefreshEvent（刷新事件）
  → 所有 @RefreshScope 的 Bean 被销毁，下次使用时重建
  → 重建时重新从 Environment 取值 → 拿到的就是新值
```

### 2.3 `spring.config.import` 是什么？（新 vs 旧）

Spring Boot 2.4 之后推荐的配置导入机制。对比老教程的 `bootstrap.yml` 方式：

| | 旧：bootstrap.yml | 新：spring.config.import（我们用的） |
|---|---|---|
| 原理 | 启动前先起"父上下文"去拉配置 | 作为配置源直接导入，更轻 |
| 现状 | 需额外加 bootstrap 依赖，逐步淘汰 | 官方推荐 |
| `optional:` 前缀 | — | 远端配置不存在也能启动（学习友好） |

### 2.4 @RefreshScope 的本质（考题高发区）

`@Value` 的注入只发生在 **Bean 创建那一刻**。配置变了，Environment 里的值其实是新的，
但 Bean 已经"出厂"不会再看 Environment。`@RefreshScope` 把 Bean 变成"懒加载代理"：
收到 RefreshEvent 后**清掉缓存实例**，下一个请求进来时用新配置重新 new 一个——
所以刷新后**第一次**请求才会体现新值（且该 Bean 的运行时状态会丢失，别拿它存会话数据）。

### 2.5 在微服务版图中的位置

Nacos Config 和注册中心是同一块服务器（8848）的两个功能面：注册中心管"谁活着"，
配置中心管"大家按什么参数干活"。模块 06 的 Sentinel 规则、模块 09 的 Seata 参数，
将来都可以走配置中心动态下发——学会它，后面全是复用。

## ③ 链路分析：一条配置的完整旅程

以 `user.welcome-msg` 为例（这是我们本模块的实验对象）：

```
① Nacos 控制台创建配置（Data ID=user-service.yml, Group=DEFAULT_GROUP, 格式=YAML）
      │ 内容: user:
      │        welcome-msg: "你好，来自配置中心 v1"
      ▼
② user-service 启动（或运行中监听到变更）
      │ 代码调用点：
      │ · spring.config.import 声明（application.yml:第10行附近）
      │ · NacosConfigDataLoader.fetchConfig() 拉取
      │ · ConfigDemoController 的 @Value 注入（带 @RefreshScope）
      ▼
③ curl http://localhost:8081/users/welcome → 返回 v1
      ▼
④ 控制台把 v1 改成 v2，点"发布"
      │ · 客户端 gRPC 长连接收到推送（毫秒级）
      │ · RefreshEvent → @RefreshScope Bean 失效
      ▼
⑤ 再 curl → 返回 v2。全程没重启服务！
```

**最容易出错的地方**：
1. **Data ID 写错**：必须是 `user-service.yml`（和服务名+扩展名完全一致，`.yaml` 和 `.yml` 是两个 Data ID！）；
2. **@RefreshScope 忘加**：启动时能取到值（因为走拉取），但运行期改配置永远不生效——"重启才生效"的假象；
3. **YAML 格式错误**：配置内容缩进错了，拉下来解析失败，可能直接覆盖本地配置引发启动失败。

## ④ 常见问题与解决思路

### Q1：启动报 `Config data location ... does not exist`
去掉 `optional:` 前缀时会这样——Nacos 里没有对应 Data ID 就拒绝启动。
**取舍**：核心配置（数据库地址）用非 optional（没有配置宁可起不来，fail-fast）；
演示类配置用 optional。我们全加 optional 是为了学习阶段流畅。

### Q2：改了配置，接口返回没变
排查三连：① 接口所在类加 `@RefreshScope` 没有；② 改的 Data ID / Group 对不对；
③ YAML 内容格式对不对（控制台"示例代码"按钮能校验）。90% 是没加 @RefreshScope。

### Q3：远程配置和本地 application.yml 同时有同一个 key，听谁的？
**远程赢**（`spring.config.import` 导入的配置优先级更高）。这也是双刃剑：
调试时你改本地 yml 发现"不生效"，八成是远端同名 key 盖住了你。

### Q4：哪些配置适合放配置中心？
✅ 会变的：限流阈值、开关、文案、数据库地址（多环境）
❌ 别放的：服务自己启动必需的引导配置（`spring.application.name`、`server.port`、
Nacos 地址本身——鸡生蛋问题：连不上配置中心就没配置可拉）。
**密码类**：放配置中心比放 git 仓库安全得多（控制台有修改审计、可加密），
生产再进一步用加密插件或密钥管理系统。我们模块 09 前会把数据源配置迁到 Nacos。

### Q5：长轮询（1.x）和 gRPC 推送（2.x）的区别？
老版本客户端每 30 秒问一次"配置变了吗"（长轮询：服务端握住请求不放，有变化立刻回答）；
2.x 客户端与服务端建立 gRPC 长连接，变更直接推过来，实时性更好。你日志里的
`Grpc connection connect` 就是它——和模块 01 注册心跳是同一条连接。

## ⑤ 动手实践（约 15 分钟）

### 第 1 步：重启 user-service
IDEA：Maven 刷新 → 重启 UserApplication（pom 加了 nacos-config 依赖）。
启动日志能看到拉取配置的记录；因为加了 `optional:`，Nacos 里还没有配置也能正常起。

### 第 2 步：验证"默认值"
```bash
curl http://localhost:8081/users/welcome
# 预期：{"code":0,"data":"本地默认欢迎语（Nacos 里还没有 user-service.yml 配置）"}
```

### 第 3 步：在 Nacos 创建配置
控制台 → **配置管理 → 配置列表 → ➕创建配置**：
| 字段 | 值 |
|------|-----|
| Data ID | `user-service.yml`（一字不差！） |
| Group | DEFAULT_GROUP |
| 配置格式 | YAML |
| 配置内容 | 见下 |

```yaml
user:
  welcome-msg: "你好，我是来自 Nacos 配置中心的消息 v1"
```
→ **发布**。

### 第 4 步：不重启，直接看效果
```bash
curl http://localhost:8081/users/welcome
# 预期：..."你好，我是来自 Nacos 配置中心的消息 v1"
```
（启动时已 import 远程配置；如果此时服务重启过一次也一样。）

### 第 5 步：见证"动态刷新"（本模块高潮）
控制台编辑该配置，把 v1 改成 **v2** → 发布 → **等 1~2 秒** → 再 curl：
```bash
curl http://localhost:8081/users/welcome
# 预期：...v2 —— 服务全程没重启！
```

### 第 6 步：提交（导师已完成，对照学习）
```bash
git add -A
git commit -m "feat(module-02): 接入 Nacos 配置中心（动态刷新验证）"
git push origin main
```
改动：两 pom 加 nacos-config 依赖、两 yml 加 config.import 并统一 server-addr 简写、
新增 ConfigDemoController（@RefreshScope + @Value）。

## ⑥ 学习检查（答题后结业）

**第 1 题（简答）**：把 `spring.config.import` 里的 `optional:` 前缀去掉会发生什么？
生产环境的"数据库连接配置"该不该加 optional？说出你的取舍逻辑。

**第 2 题（简答）**：你在控制台改了配置但接口始终返回旧值。请按概率从高到低列出至少 3 个可能原因，
并说出最可能原因的底层原理（提示：Bean 生命周期 + @Value 注入时机）。

**第 3 题（简答/开放）**：数据库密码应该存在本地 application.yml 还是 Nacos 配置中心？
至少给出两条理由，并说说"存在 Nacos 就绝对安全了吗"——还有什么风险？（开放题，考察安全思维）
