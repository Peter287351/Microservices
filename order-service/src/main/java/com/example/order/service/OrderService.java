package com.example.order.service;

import com.example.common.api.BusinessException;
import com.example.common.api.ErrorCode;
import com.example.order.dto.OrderCreateRequest;
import com.example.order.entity.Order;
import com.example.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order getById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    public List<Order> listByUser(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 下单（模块 00 单体版）：生成订单号 → 落库 → 返回。
     *
     * TODO(模块 03 OpenFeign)：真正的电商下单必须先调 user-service 校验用户是否存在，
     * 那时这里将变成跨服务调用，也是整条微服务链路最有意思的地方。
     */
    @Transactional
    public Order create(OrderCreateRequest request) {
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(request.userId());
        order.setProductName(request.productName());
        order.setAmount(request.amount());
        order.setStatus("CREATED");
        return orderRepository.save(order);
    }

    /** 生成订单号：时间戳 + 3 位随机数，够学习用；生产环境一般用雪花算法或发号器 */
    private String generateOrderNo() {
        long timestamp = System.currentTimeMillis();
        int random = ThreadLocalRandom.current().nextInt(100, 1000);
        return "ORD" + timestamp + random;
    }
}
