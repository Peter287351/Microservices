package com.example.order.client;

import com.example.common.api.Result;
import com.example.order.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * user-service 的远程调用客户端（模块 03）。
 *
 * 这是一个接口，没有任何实现类——Feign 在运行时生成"动态代理"对象，
 * 把方法调用翻译成 HTTP 请求。name = 注册中心里的服务名（模块 01 注册的身份证），
 * Feign 拿着这个名字去 Nacos 查实例地址，而不是写死 IP:端口。
 *
 * 注意：Feign 接口里的 @PathVariable/@RequestParam 必须显式写参数名——
 * 接口编译后参数名默认丢失，不写名字 Feign 不知道把值填到哪里。
 * 模块 09 起，这些调用会被 Seata 拦截：请求头自动携带全局事务 XID。
 */
@FeignClient(name = "user-service")
public interface UserClient {

    /** 对应 user-service 的 UserController.getById()：GET /users/{id} */
    @GetMapping("/users/{id}")
    Result<UserDTO> getById(@PathVariable("id") Long id);

    /** 对应 user-service 的 AccountController.deduct()：POST /accounts/deduct（模块09 全局事务分支①） */
    @PostMapping("/accounts/deduct")
    Result<BigDecimal> deductBalance(@RequestParam("userId") Long userId, @RequestParam("amount") BigDecimal amount);

    /** 对应 user-service 的 AccountController.balance()：GET /accounts/balance（模块09 实验观察用） */
    @GetMapping("/accounts/balance")
    Result<BigDecimal> getBalance(@RequestParam("userId") Long userId);

    /** 对应 user-service 的 InstanceController.instanceInfo()：GET /users/instance-info（模块04实验用） */
    @GetMapping("/users/instance-info")
    Result<java.util.Map<String, Object>> getInstanceInfo();
}
