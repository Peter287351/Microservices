package com.example.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 订单服务启动类（端口 8082）。
 * scanBasePackages 说明同 user-service：为了加载公共模块里的全局异常处理器。
 * @EnableFeignClients（模块 03）：扫描 @FeignClient 接口，运行时生成动态代理，
 * 让"调用一个接口方法"变成"发一个 HTTP 请求"。
 */
@SpringBootApplication(scanBasePackages = "com.example")
@EnableFeignClients
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
