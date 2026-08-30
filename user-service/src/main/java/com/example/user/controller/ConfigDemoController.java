package com.example.user.controller;

import com.example.common.api.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配置中心演示接口（模块 02）。
 *
 * @Value：从 Environment 里取 user.welcome-msg 的值，取不到时用冒号后面的默认值；
 * @RefreshScope：配置变更时把这个 Bean "销毁重建"，新值才能注入进来——
 *                不加的话 @Value 只在 Bean 创建时注入一次，之后永远不变。
 */
@RefreshScope
@RestController
@RequestMapping("/users")
public class ConfigDemoController {

    @Value("${user.welcome-msg:本地默认欢迎语（Nacos 里还没有 user-service.yml 配置）}")
    private String welcomeMsg;

    /** GET /users/welcome：观察这个接口的返回如何随 Nacos 配置变化 */
    @GetMapping("/welcome")
    public Result<String> welcome() {
        return Result.ok(welcomeMsg);
    }
}
