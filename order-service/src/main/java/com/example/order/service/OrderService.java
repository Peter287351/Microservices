package com.example.order.service;

import com.example.common.api.BusinessException;
import com.example.common.api.ErrorCode;
import com.example.common.api.Result;
import com.example.common.event.OrderCreatedEvent;
import com.example.order.client.UserClient;
import com.example.order.dto.OrderCreateRequest;
import com.example.order.dto.UserDTO;
import com.example.order.entity.Order;
import com.example.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final UserClient userClient;
    private final StreamBridge streamBridge;

    public OrderService(OrderRepository orderRepository, UserClient userClient, StreamBridge streamBridge) {
        this.orderRepository = orderRepository;
        this.userClient = userClient;
        this.streamBridge = streamBridge;
    }

    public Order getById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    public List<Order> listByUser(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 下单（模块 03 微服务版）：先【跨进程】校验用户 → 生成订单号 → 落库。
     *
     * validateUser() 那一行背后发生的事（详见 module-03 README 第③节）：
     * Feign 动态代理 → 拿服务名查 Nacos（模块01的电话簿）→ LoadBalancer 选一个实例
     * → 发 HTTP GET http://192.168.x.x:8081/users/{id} → 拿回 JSON 反序列化成 Result<UserDTO>
     */
    @Transactional
    public Order create(OrderCreateRequest request) {
        validateUser(request.userId());

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(request.userId());
        order.setProductName(request.productName());
        order.setAmount(request.amount());
        order.setStatus("CREATED");
        Order saved = orderRepository.save(order);

        // 模块 08：下单成功后发布"订单已创建"事件（异步广播，不阻塞下单响应）。
        // StreamBridge 把事件发往 orderCreated-out-0 通道 → RocketMQ 的 order-created-topic，
        // 谁关心谁订阅（目前 user-service 消费它做后续动作，如加积分、发通知）。
        //
        // 注意（E08-3 教训）：MQ 发送失败【不能让下单失败】——订单是主业务，事件是附属品。
        // 这里选择"记录错误、继续返回"；生产环境的正确姿势是"本地消息表/事务消息"保证事件最终不丢。
        OrderCreatedEvent event = new OrderCreatedEvent(saved.getId(), saved.getOrderNo(),
                saved.getUserId(), saved.getProductName(), saved.getAmount(), saved.getCreatedAt());
        try {
            boolean sent = streamBridge.send("orderCreated-out-0", event);
            log.info("订单创建事件{}：orderNo={}", sent ? "已发送" : "发送失败", saved.getOrderNo());
        } catch (Exception e) {
            log.error("订单创建事件发送失败（MQ 不可用）。订单已正常创建：orderNo={}，" +
                    "生产环境应用本地消息表/事务消息保证事件不丢", saved.getOrderNo(), e);
        }
        return saved;
    }

    /** 远程校验用户：对方返回非 0 错误码或没数据，都视为"用户不存在" */
    private void validateUser(Long userId) {
        Result<UserDTO> response = userClient.getById(userId);
        if (response.getCode() != ErrorCode.SUCCESS.getCode() || response.getData() == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }

    /** 生成订单号：时间戳 + 3 位随机数，够学习用；生产环境一般用雪花算法或发号器 */
    private String generateOrderNo() {
        long timestamp = System.currentTimeMillis();
        int random = ThreadLocalRandom.current().nextInt(100, 1000);
        return "ORD" + timestamp + random;
    }
}
