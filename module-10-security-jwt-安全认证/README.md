# 模块 10：Spring Security + JWT 安全认证 —— 给微服务王国上锁

> 目标：新增 auth-service（9000）签发 JWT；网关升级为"验票员"——
> **没有合法令牌，任何业务接口一律 401**。至此系统正式"上锁"。

---

## ① 项目背景：一直在"裸奔"

到目前为止，任何人知道端口号就能：查用户、下订单、扣余额（真的！模块 09 我们裸调了扣款接口）。
真实电商不能这样。本模块引入完整的认证体系：

```
登录（用户名+密码）→ auth-service 验明正身 → 签发 JWT"门票"
后续每个请求 → 网关验票（签名+有效期）→ 放行转发（业务服务零感知）
```

**为什么鉴权放网关？** 写一次，所有服务受保护（模块 05 网关"横切关注点集中地"的兑现）；
业务服务保持纯粹，连安全代码都不用写。

## ② 概念文档：认证、JWT、验票

### 2.1 认证（Authentication）vs 授权（Authorization）

- **认证 = 你是谁**（登录：验用户名密码）
- **授权 = 你能干什么**（权限：这个接口你有没有资格调）
本模块完成认证 + 网关级"是否登录"的粗粒度授权；细粒度权限（管理员/普通用户）同理扩展 claims。

### 2.2 JWT 三段结构（拆开一个真实令牌）

```
eyJhbGciOiJIUzI1NiJ9 . eyJzdWIiOiJ6aGFuZ3NhbiIsInVzZXJJZCI6MX0 . SflKxwRJSM...
     ↑ Header（算法）           ↑ Payload（声明：用户名/userId/过期时间）      ↑ Signature（签名）
```

- **Payload 只是 Base64 编码，不是加密**——谁都能解码看内容，所以**敏感信息绝不能放**；
- **Signature 防篡改**：用只有服务端知道的密钥对前两段签名，改一个字符签名就对不上；
- **无状态的精髓**：服务端不存 session，验票只需"密钥验签 + 看过期时间"——天然适合多服务/多实例。

### 2.3 架构图

```
① POST /api/auth/login {zhangsan/123456}
        │ （网关唯一放行的入口）
        ▼
┌─ auth-service :9000 ─────────────────────────────┐
│ AuthenticationManager 验账号密码（Spring Security）│
│ 通过 → NimbusJwtEncoder 签发 HS256 JWT            │
│       （claims: sub/userId/exp/...）              │
└──────────────┬───────────────────────────────────┘
               │ {"token":"eyJ...","tokenType":"Bearer"}
               ▼
② GET /api/orders?userId=1  +  Header: Authorization: Bearer eyJ...
        │
        ▼
┌─ gateway :8080（资源服务器）──────────────────────┐
│ 验签名（同一把密钥）→ 验有效期 → 放行              │
│ 无 token / 签名错 / 过期 → 401                    │
└──────────────┬────────────────────────────────────┘
               ▼ （转发，下游业务零感知）
        order-service 照常处理
```

### 2.4 与"完整 Spring Authorization Server"的关系

我们用 Spring Security 标准认证流程 + **NimbusJwtEncoder** 签发令牌——这套组件就是
Spring Authorization Server（SAS）的底层。SAS 完整版额外提供 OAuth2 标准流程
（授权码模式/客户端凭证模式/refresh_token）和令牌端点规范；本教学版实现的是等价的
"密码登录 + JWT"主链路。升级路径清晰：需要第三方登录/开放平台时再上完整 SAS，概念无缝衔接。

### 2.5 在微服务版图中的位置

安全层是最后一层"横切关注点"：登录入口（auth-service）+ 验票关卡（gateway）+ 无感业务（其余服务）。
至此：注册中心/配置/调用/网关/限流/追踪/消息/事务/**安全**——九大治理能力集齐。

## ③ 链路分析：一次带票请求的完整旅程

| # | 步骤 | 代码位置 |
|---|------|---------|
| 1 | 登录：账号密码进 AuthenticationManager | [AuthController.login](../auth-service/src/main/java/com/example/auth/controller/AuthController.java) |
| 2 | Spring Security 比对凭证 | [SecurityConfig 的 InMemoryUserDetailsManager](../auth-service/src/main/java/com/example/auth/config/SecurityConfig.java) |
| 3 | 签发 JWT（HS256 + claims） | AuthController 的 NimbusJwtEncoder 段 |
| 4 | 带 `Authorization: Bearer <token>` 请求业务接口 | —— |
| 5 | 网关验签+验期 | [gateway SecurityConfig](../gateway-service/src/main/java/com/example/gateway/config/SecurityConfig.java) |
| 6 | 放行转发（业务服务无感） | 原有链路 |

**容易出错的地方**：
1. **两边密钥不一致** → 所有请求 401（token 是 auth 签的、网关用另一把钥匙验，必失败）；
2. **HS256 密钥少于 32 字节** → 启动报 WeakKeyException；
3. 忘带 `Bearer ` 前缀 / Header 拼错 → 401；
4. Payload 放了敏感信息（密码/手机号）→ 被解码泄露（签名防篡改、**不防读**）。

## ④ 常见问题与解决思路

### Q1：所有请求都 401，连登录后也 401
按序排查：① token 完整复制了吗（`Bearer ` + 空格 + token，一个字符不能少）；
② 网关与 auth 的 **secret 是否一致**；③ token 过期没（exp 字段，2 小时）；④ 网关重启过没（改了 yml 要重启）。

### Q2：登录返回 401"用户名或密码错误"
教学账号是 zhangsan/123456（内存里写死的）；确认密码没敲错。真实项目应接数据库+BCrypt。

### Q3：auth-service 启动失败 `WeakKeyException`
HS256 要求密钥 ≥32 字节。我们配的 58 字节没问题；自己改 secret 时注意长度。

### Q4：退出登录怎么做？JWT 没有 session 可删
JWT 的代价：签发后**无法作废**（除非等过期）。缓解三板斧：①短时效（我们 2 小时）；
②HTTPS 防中间人截获；③需要"强制下线"时上 **refresh token + 黑名单**（Redis 记注销的 token）。
这也是面试高频题。

### Q5：HS256（对称）vs RS256（非对称）怎么选？
HS256：一把钥匙签+验，简单快，但**每个验票方都得持有签发密钥**（网关多了钥匙扩散风险）。
RS256：私钥签名（只在 auth），公钥验票（可发给任何服务/第三方）——生产多服务推荐。
切换成本：换密钥配置即可，代码不变（Spring Security 自动识别算法）。

## ⑤ 动手实践（20 分钟）

### 第 1 步：启动认证服务
Maven 刷新（父 pom 加了 auth-service）→ 启动 **AuthApplication**（9000）→ 重启 **GatewayApplication**（新增了路由/安全配置/依赖）。

### 第 2 步：见证"上锁"
```bash
curl -i "http://localhost:8080/api/orders?userId=1"
# 预期：HTTP/1.1 401 Unauthorized（之前裸奔的接口，现在要票了）
```

### 第 3 步：登录拿票
```bash
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
     -d '{"username":"zhangsan","password":"123456"}'
# 预期：{"code":0,"data":{"token":"eyJ...","tokenType":"Bearer","expiresIn":7200}}
# 把 token 复制下来（很长）
```

### 第 4 步：凭票通行
```bash
curl -H "Authorization: Bearer 把token粘到这里" "http://localhost:8080/api/orders?userId=1"
# 预期：code:0 订单列表 —— 上锁后第一次合法通行
curl -X POST -H "Authorization: Bearer token" -H "Content-Type: application/json; charset=utf-8" \
     --data-binary @/tmp/order.json http://localhost:8080/api/orders
# 下单也正常（Seata/MQ/追踪全链路在"有锁"状态下继续工作）
```

### 第 5 步：错误密码
```bash
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
     -d '{"username":"zhangsan","password":"wrong"}'
# 预期：{"code":401,"message":"用户名或密码错误"}
```

### 第 6 步：提交（导师已完成，供对照）
```bash
git commit -m "feat(module-10): 集成 Spring Security + JWT（auth-service + 网关统一鉴权）"
```

### 选做进阶
①把 token 粘到 [jwt.io](https://jwt.io) 解码，亲眼看 Payload 三段结构（注意：能看不能改）；
②给 Seata/Zipkin 链路里的登录请求找 Trace（登录也在追踪范围内）。

## ⑥ 学习检查

**第 1 题（简答）**：认证和授权的区别？本模块分别在哪里完成了它们？

**第 2 题（简答）**：JWT 三段各是什么？为什么"服务端不存任何 session"也能验出你是谁、票有没有过期？（对比传统 session 机制）

**第 3 题（开放）**：token 被偷了怎么办？列出至少 3 种缓解手段，并说明为什么"无法主动作废"是 JWT 与生俱来的弱点。

---

## 🎓 结课倒计时

模块 10 通过后，只剩 **module-99 结课综合实战**：全链路串联复盘 + 从零复现总检验 + 面试考点串讲。
九大治理能力已全部到位——你已经有了一个简历级别的微服务项目。
