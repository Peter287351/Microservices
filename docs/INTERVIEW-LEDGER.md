# 📒 INTERVIEW-LEDGER · 面试复盘与错题账本

> 用法：每次 `@面试 X` 结束后，教练把**薄弱点 + 满分答法**追加到第三节；
> 每次 `@埋雷 X` 破案后，按第二节 Post-Mortem 模板登记。
> 求职前只需重读本文件 + [快问快答与踩坑速查卡](快问快答与踩坑速查卡.md)。

---

## 一、高频八股得分点汇总（持续追加）

| # | 主题 | 一句话得分点 | 来源 | 掌握度 |
|---|------|-------------|------|--------|
| 1 | Nacos 心跳剔除 | 2.x gRPC 长连接代替 1.x 心跳；连接断=实例失联，15s 标不健康 30s 剔除 | 模块01 实验 | ✅ |
| 2 | Nacos AP/CP | 临时实例(默认)=AP Distro；持久实例与配置=CP Raft | Stage 1 | ⬜ |
| 3 | Feign 源码链路 | @EnableFeignClients→Registrar→FactoryBean→动态代理→SynchronousMethodHandler | Stage 1 | ⬜ |
| 4 | Connect vs Read Timeout | 握手时限 vs 等响应时限；只有"确定未达"才可安全重试 | Stage 1/2 | ⬜ |
| 5 | 重试资损 | 读超时时请求可能已生效，重试=重复扣减 → 幂等是重试的前提 | Stage 2 | ⬜ |
| 6 | 三种幂等取舍 | Token(性能好需前端配合) / 唯一索引(最可靠) / 状态机CAS(单实体流转)；生产组合使用 | Stage 2 | ⬜ |
| 7 | @Transactional 失效 | 自调用/非public/异常被吞/rollbackFor/多线程 | Stage 3 | ⬜ |
| 8 | 长事务内嵌 RPC | 事务持连接等远程→连接池打满→全服务级联 | Stage 3 | ⬜ |
| 9 | Seata AT 全局锁 | TC 按 表+行 锁防两个全局事务写同链；回滚前校验后镜像防脏写 | 模块09+Stage 3 | ⬜ |
| 10 | 本地消息表 | 业务与消息同事务落库→扫描投递→对账兜底，最终一致 | Stage 3 | ⬜ |
| 11 | Sentinel SlotChain | NodeSelector→ClusterBuilder→Statistic→Flow→Degrade；Statistic 先放行后记账 | Stage 4 | ⬜ |
| 12 | LeapArray 滑动窗口 | sampleCount 个窗口循环复用，统计 pass/block/RT；窗口越细越准越贵 | Stage 4 | ⬜ |
| 13 | 令牌桶 vs 漏桶 | 允许突发 vs 恒速整流；Sentinel Warm Up=冷启动令牌桶，排队等待=漏桶 | Stage 4 | ⬜ |
| 14 | Gateway 为什么不能阻塞 | EventLoop 被一个同步调用卡住=其上所有连接卡死；同步 IO 必须 publishOn 弹性池 | Stage 5 | ⬜ |
| 15 | TraceId 跨线程 | 跨进程靠 Header(b3/traceparent)；跨线程池靠 TaskDecorator/TTL 复制 MDC 上下文 | Stage 5 | ⬜ |

## 二、线上排错 Post-Mortem 记录表（@埋雷 破案后登记）

> 模板（复制使用）：

```
### PM-<序号>：<一句话故障标题>（关联错误知识库 E-xx）
- 日期/关卡：2026-xx-xx / Stage-x
- 工单现象：xxx
- 我的排查路径：现象→假设→验证动作→推翻/证实（写真实过程，含走错的弯路）
- 根因：xxx
- 修复：xxx
- 为什么隐蔽：xxx（这个 Bug 骗过我的点是什么）
- 预防措施：xxx（代码/配置/流程层面）
- 面试可讲点：xxx
```

### 已登记

（暂无——Stage 1 埋雷后开始积累）

## 三、我的答题薄弱点追踪（@面试 后由教练追加）

> 格式：`[日期][Stage-x][第几轮] 问题描述 → 薄弱本质 → 满分答法要点 → 复习状态(⬜/🔁/✅)`

（暂无）
