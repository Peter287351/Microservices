package com.example.user.controller;

import com.example.common.api.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 实例身份牌（模块 04）：告诉调用方"你这次请求落在了我哪个端口上"。
 * user-service 马上要跑两个实例（8081 和 8083），LoadBalancer 轮询时
 * 靠这个接口区分"这次是谁接的客"。
 */
@RestController
@RequestMapping("/users")
public class InstanceController {

    /** 注入当前实例自己的端口（同一份代码，两个实例端口不同） */
    @Value("${server.port}")
    private int port;

    /** GET /users/instance-info */
    @GetMapping("/instance-info")
    public Result<Map<String, Object>> instanceInfo() {
        return Result.ok(Map.of(
                "service", "user-service",
                "port", port,
                "time", LocalDateTime.now().toString()));
    }
}
