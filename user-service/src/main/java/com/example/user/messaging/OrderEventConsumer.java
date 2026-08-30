package com.example.user.messaging;

import com.example.common.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * 订单事件的消费者（模块 08）。
 *
 * 函数式消费：Bean 名 orderCreated 对应配置里的
 * spring.cloud.function.definition=orderCreated 和绑定通道 orderCreated-in-0。
 * 收到消息后能做的事：加积分、发短信、更新推荐模型……本教学项目只打日志证明"收到了"。
 */
@Configuration
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    @Bean
    public Consumer<OrderCreatedEvent> orderCreated() {
        return event -> log.info(
                "[MQ] 收到订单创建事件：orderNo={}，userId={}，商品={}，金额={}（此处可扩展：加积分/发通知）",
                event.orderNo(), event.userId(), event.productName(), event.amount());
    }
}
