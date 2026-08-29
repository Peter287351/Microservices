package com.example.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户服务启动类。
 *
 * scanBasePackages = "com.example"：
 * 默认只扫描启动类所在包(com.example.user)下的 Bean，而公共模块的
 * GlobalExceptionHandler 在 com.example.common 包里，扫不到就不会生效。
 * 这里把扫描范围扩大到整个 com.example，公共模块的 Bean 才会被加载。
 */
@SpringBootApplication(scanBasePackages = "com.example")
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
