package com.example.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * TODO [Stage-5]: 全局过滤器——鉴权信息染色 + 自定义链路标记。
 *
 * 任务（详见 docs/05-STAGE-5-GATEWAY-TRACE.md 第 2 节任务 1）：
 *  1. 实现 GlobalFilter, Ordered；从 SecurityContext（Reactive）取出当前 JWT 的 userId claim，
 *     染色为请求头 X-User-Id 传给下游（下游服务可从 Header 直接读，不必重复解析 JWT）；
 *  2. Order 取值要思考：必须在【路由转发之前】执行——放在负数段还是正数段？为什么？
 *     （对照：SecurityWebFilterChain 的过滤发生在更外层，本过滤器拿到的是已验票的 exchange）
 *  3. 验证：带 JWT 请求 /api/orders/**，在 order-service 打印/日志确认收到 X-User-Id: 1；
 *  4. 延伸思考：为什么这里用 exchange.mutate().request(...) 构造新 exchange，而不是直接改？
 *
 * 响应式要点：所有逻辑必须返回 Mono 链，禁止任何同步阻塞调用（Stage 5 手册 1.1 节红线）。
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // TODO [Stage-5]: 取 JWT claims → 染色 X-User-Id → chain.filter(mutatedExchange)
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // TODO [Stage-5]: 返回你选定的 Order 值，并在注释里写明理由
        return 0;
    }
}
