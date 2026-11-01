package com.example.inventory;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 库存服务启动类（端口 8084）—— 五关进阶的"主战场"服务。
 *
 * 与 user/order 服务一样：scanBasePackages 扩到 com.example 才能加载 common 的全局异常处理器。
 * MapperScan 指向本服务的 mapper 包（MyBatis-Plus 的 BaseMapper 由此被发现）。
 */
@SpringBootApplication(scanBasePackages = "com.example")
@MapperScan("com.example.inventory.mapper")
public class InventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }
}
