package com.example.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关启动类（端口 8080）：全系统的唯一大门。
 * 注意本模块没有依赖 common（common 传递 spring-boot-starter-web，
 * 会和 Gateway 的 WebFlux 冲突），所以默认包扫描 com.example.gateway 即可。
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
