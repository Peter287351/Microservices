package com.example.user.controller;

import com.example.common.api.Result;
import com.example.user.dto.UserCreateRequest;
import com.example.user.entity.User;
import com.example.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户接口层：只做"接收请求 → 调 Service → 包装 Result 返回"三件事。
 * 接口前缀统一 /users，服务启动后通过 8081 端口访问。
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 查询单个用户：GET /users/1 */
    @GetMapping("/{id}")
    public Result<User> getById(@PathVariable Long id) {
        return Result.ok(userService.getById(id));
    }

    /** 查询全部用户：GET /users */
    @GetMapping
    public Result<List<User>> listAll() {
        return Result.ok(userService.listAll());
    }

    /** 创建用户：POST /users，请求体 JSON，@Valid 触发参数校验 */
    @PostMapping
    public Result<User> create(@Valid @RequestBody UserCreateRequest request) {
        return Result.ok(userService.create(request));
    }
}
