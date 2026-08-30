package com.example.user.controller;

import com.example.common.api.Result;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 服务发现观察窗（模块 01）：让我们"看见"注册中心里有什么。
 * DiscoveryClient 是 Spring Cloud 的统一发现接口，底层由 Nacos 实现——
 * 模块 03 的 OpenFeign、模块 04 的 LoadBalancer 内部都靠它拿实例列表。
 */
@RestController
@RequestMapping("/discovery")
public class DiscoveryController {

    private final DiscoveryClient discoveryClient;

    public DiscoveryController(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    /** 注册中心里有哪些服务：GET /discovery */
    @GetMapping
    public Result<List<String>> services() {
        return Result.ok(discoveryClient.getServices());
    }

    /** 某个服务的所有存活实例（IP+端口）：GET /discovery/user-service */
    @GetMapping("/{serviceName}")
    public Result<List<Map<String, Object>>> instances(@PathVariable String serviceName) {
        List<Map<String, Object>> list = discoveryClient.getInstances(serviceName).stream()
                .map(i -> Map.<String, Object>of(
                        "serviceId", i.getServiceId(),
                        "uri", i.getUri().toString(),
                        "host", i.getHost(),
                        "port", i.getPort()))
                .toList();
        return Result.ok(list);
    }
}
