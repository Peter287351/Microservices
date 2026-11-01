package com.example.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * TODO [Stage-5]: 异步线程池 + TaskDecorator（TraceId 跨线程不丢）。
 *
 * 任务（详见 docs/05-STAGE-5-GATEWAY-TRACE.md 第 1.4/任务3）：
 *  1. 定义 ThreadPoolTaskExecutor Bean（核心/最大/队列自拟并说明理由）；
 *  2. 实现 TaskDecorator：提交任务时快照当前线程 MDC（含 traceId），执行前 set 回去、结束后清理；
 *  3. 方法上加 @EnableAsync；写一个 @Async 方法打印 MDC.get("traceId")；
 *  4. 实验验证：改造前 @Async 日志里 traceId 为空 → 装饰后与主线程一致。
 *
 * 提示：MDC 是 SLF4J 的 Map 结构（ThreadLocal 实现），快照可用 MDC.getCopyOfContextMap()。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    // TODO [Stage-5]: Bean 名如 "traceExecutor"，TaskDecorator 逻辑写在 TODO 处
    @Bean
    public Executor traceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("trace-");
        // TODO [Stage-5]: executor.setTaskDecorator(...); ← 关键一行
        executor.initialize();
        return executor;
    }
}
