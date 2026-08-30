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
import io.seata.spring.annotation.GlobalTransactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

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
    /**
     * 下单（模块 09 微服务版）：扣账户余额 + 创建订单，两个服务的两次写【同生共死】。
     *
     * @GlobalTransactional：本方法是全局事务发起方（TM）。
     *  分支①：user-service 扣余额（UPDATE t_account，Seata 数据源代理自动写 undo_log）
     *  分支②：order-service 插订单（INSERT t_order，同上）
     *  任一环节抛异常 → TM 通知 TC 全局回滚 → 两个分支按 undo_log 镜像逆向补偿。
     *
     * 失败注入：商品名包含"炸弹" = 模拟"扣款成功后、订单提交前"的突发失败，
     * 没有全局事务时会造成"钱扣了、单没建"的数据不一致——有 Seata 则双双回滚。
     */
    @GlobalTransactional(rollbackFor = Exception.class)
    public Order create(OrderCreateRequest request) {
        validateUser(request.userId());

        // 分支①：远程扣减账户余额（Feign 请求头自动携带全局事务 XID）
        userClient.deductBalance(request.userId(), request.amount());

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(request.userId());
        order.setProductName(request.productName());
        order.setAmount(request.amount());
        order.setStatus("CREATED");
        Order saved = orderRepository.save(order);

        // 失败注入（模块 09）：验证全局回滚
        if (request.productName().contains("炸弹")) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "风控拦截：模拟下单失败，验证全局回滚");
        }

        // 模块 08：下单成功后发布"订单已创建"事件（异步广播，不阻塞下单响应）。
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
