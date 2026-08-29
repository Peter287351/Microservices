package com.example.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 下单请求参数。
 * 下单接口：POST /orders，请求体形如
 * {"userId": 1, "productName": "机械键盘", "amount": 199.00}
 */
public record OrderCreateRequest(
        @NotNull(message = "userId 不能为空")
        Long userId,

        @NotBlank(message = "商品名不能为空")
        @Size(max = 100, message = "商品名最长 100 字符")
        String productName,

        @NotNull(message = "金额不能为空")
        @DecimalMin(value = "0.01", message = "金额必须大于 0")
        BigDecimal amount
) {
}
