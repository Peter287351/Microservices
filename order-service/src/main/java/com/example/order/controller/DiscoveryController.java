package com.example.order.controller;

import com.example.common.api.Result;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 服务发现观察窗（模块 01）：同 user-service 的 DiscoveryController。
 * 试着在这里查 user-service 的实例列表——这就是模块 03 Feign 调用前要做的第一步。
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

    /** 某个服务的所有存活实例：GET /discovery/user-service */
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
