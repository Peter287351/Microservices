package com.example.order.client;

import com.example.common.api.Result;
import com.example.order.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * user-service 的远程调用客户端（模块 03）。
 *
 * 这是一个接口，没有任何实现类——Feign 在运行时生成"动态代理"对象，
 * 把方法调用翻译成 HTTP 请求。name = 注册中心里的服务名（模块 01 注册的身份证），
 * Feign 拿着这个名字去 Nacos 查实例地址，而不是写死 IP:端口。
 *
 * 注意：Feign 接口里的 @PathVariable 必须显式写参数名（"id"）——
 * 接口编译后参数名默认丢失，不写名字 Feign 不知道把值填进 URL 的哪个占位符。
 */
@FeignClient(name = "user-service")
public interface UserClient {

    /** 对应 user-service 的 UserController.getById()：GET /users/{id} */
    @GetMapping("/users/{id}")
    Result<UserDTO> getById(@PathVariable("id") Long id);

    /** 对应 user-service 的 InstanceController.instanceInfo()：GET /users/instance-info（模块04实验用） */
    @GetMapping("/users/instance-info")
    Result<java.util.Map<String, Object>> getInstanceInfo();
}
