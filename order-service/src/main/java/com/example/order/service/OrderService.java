package com.example.order.service;

import com.example.common.api.BusinessException;
import com.example.common.api.ErrorCode;
import com.example.common.api.Result;
import com.example.order.client.UserClient;
import com.example.order.dto.OrderCreateRequest;
import com.example.order.dto.UserDTO;
import com.example.order.entity.Order;
import com.example.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserClient userClient;

    public OrderService(OrderRepository orderRepository, UserClient userClient) {
        this.orderRepository = orderRepository;
        this.userClient = userClient;
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
        return orderRepository.save(order);
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
