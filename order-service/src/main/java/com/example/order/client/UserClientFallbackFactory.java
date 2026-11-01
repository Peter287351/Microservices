package com.example.order.client;

import com.example.common.api.Result;
import com.example.order.dto.UserDTO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * TODO [Stage-4]: UserClient 的降级工厂（本关核心任务，骨架先立）。
 *
 * 实现要点（详见 docs/04-STAGE-4-SENTINEL.md 第 1.5 节）：
 *  1. 实现 feign FallbackFactory<UserClient>，create(Throwable cause) 返回匿名实现；
 *  2. 按 cause 类型区分降级语义：
 *     - CallNotPermittedException（sentinel）→ 熔断打开中，返回"系统繁忙"兜底 DTO
 *     - ReadTimeout/ConnectException          → 网络未达/超时，返回可重试标记
 *     - 其他                                   → 记 ERROR 日志（带 traceId）+ 通用兜底
 *  3. 完成后在 @FeignClient(name="user-service", fallbackFactory = UserClientFallbackFactory.class)
 *     挂上，并开启 feign.sentinel.enabled=true；
 *  4. 思考点：fallback 里返回的兜底 DTO，下游拿到后凭什么知道"这是降级数据"？(约定字段/状态位)
 */
@Component
public class UserClientFallbackFactory /* TODO [Stage-4]: implements FallbackFactory<UserClient> */ {

    // TODO [Stage-4]: create(Throwable cause) → 返回 UserClient 的降级实现
}
