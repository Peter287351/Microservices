package com.example.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建用户的请求参数（DTO：Data Transfer Object，只负责接口层的进出参数）。
 * 注解 = 校验规则，Controller 里配合 @Valid 触发，不合法会被全局异常处理器拦下。
 */
public record UserCreateRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 50, message = "用户名最长 50 字符")
        String username,

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email,

        String phone
) {
}
