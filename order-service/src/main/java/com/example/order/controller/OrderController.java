package com.example.order.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.example.common.api.Result;
import com.example.order.client.UserClient;
import com.example.order.dto.OrderCreateRequest;
import com.example.order.entity.Order;
import com.example.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单接口层（端口 8082）。
 * 模块 05 起，外部请求将改从网关 8080 进入，由网关转发到这里。
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final UserClient userClient;

    public OrderController(OrderService orderService, UserClient userClient) {
        this.orderService = orderService;
        this.userClient = userClient;
    }

    /**
     * 下单：POST /orders。
     * @SentinelResource（模块 06）：把"createOrder"注册成 Sentinel 资源。
     * 控制台给它配流控规则（如 QPS=1）后，超限的请求不再执行业务，
     * 而是走 fallback 指向的兜底方法——限流时用户看到的是友好提示而非报错。
     */
    @PostMapping
    @SentinelResource(value = "createOrder", fallback = "createOrderFallback")
    public Result<Order> create(@Valid @RequestBody OrderCreateRequest request) {
        return Result.ok(orderService.create(request));
    }

    /**
     * 下单的兜底方法（模块 06）：签名 = 原方法参数 + 末尾一个 Throwable，
     * Sentinel 触发限流/熔断/业务异常时自动代替原方法执行。
     * 注意：兜底方法必须在同一个类里，且签名不匹配就不生效（新手第一坑）。
     */
    public Result<Order> createOrderFallback(OrderCreateRequest request, Throwable t) {
        return Result.fail(429, "下单太火爆啦，请稍后再试（Sentinel 已保护此接口）");
    }

    /** 查询订单：GET /orders/1 */
    @GetMapping("/{id}")
    public Result<Order> getById(@PathVariable Long id) {
        return Result.ok(orderService.getById(id));
    }

    /** 查某用户的所有订单：GET /orders?userId=1 */
    @GetMapping
    public Result<List<Order>> listByUser(@RequestParam Long userId) {
        return Result.ok(orderService.listByUser(userId));
    }

    /**
     * 负载均衡演示（模块 04）：连续调用 6 次 user-service 的实例身份牌接口，
     * 每次由 LoadBalancer 从实例列表里挑一个。user-service 起两个实例后，
     * 你会看到返回的端口在 8081 / 8083 之间轮流切换（轮询算法）。
     */
    @GetMapping("/lb-test")
    public Result<List<String>> loadBalanceTest() {
        List<String> hits = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            Result<java.util.Map<String, Object>> info = userClient.getInstanceInfo();
            hits.add("第" + i + "次调用 → " + info.getData().get("port") + " 端口接单");
        }
        return Result.ok(hits);
    }
}
