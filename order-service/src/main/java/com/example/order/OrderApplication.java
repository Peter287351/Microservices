package com.example.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 订单服务启动类（端口 8082）。
 * scanBasePackages 说明同 user-service：为了加载公共模块里的全局异常处理器。
 */
@SpringBootApplication(scanBasePackages = "com.example")
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
