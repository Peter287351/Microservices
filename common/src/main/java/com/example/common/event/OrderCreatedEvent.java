package com.example.common.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单创建事件（模块 08）：下单成功后通过 RocketMQ 广播。
 *
 * 放在 common 里，生产者（order-service）和消费者（user-service）
 * 共用同一份"事件契约"——消息跨服务传递的 JSON 结构双方都按它来序列化/反序列化，
 * 与 Feign 的 UserDTO 异曲同工：契约不变，两端内部随便重构。
 */
public record OrderCreatedEvent(
        Long orderId,
        String orderNo,
        Long userId,
        String productName,
        BigDecimal amount,
        LocalDateTime createdAt) {
}
