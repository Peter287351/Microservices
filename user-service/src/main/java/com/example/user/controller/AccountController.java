package com.example.user.controller;

import com.example.common.api.Result;
import com.example.user.service.AccountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 账户接口（模块 09）：被 order-service 通过 Feign 远程调用（全局事务的分支①）。
 * Seata 会把全局事务 XID 通过 Feign 请求头传进来，本服务参与全局回滚。
 */
@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /** 扣款：POST /accounts/deduct?userId=1&amount=66.60 */
    @PostMapping("/deduct")
    public Result<BigDecimal> deduct(@RequestParam Long userId, @RequestParam BigDecimal amount) {
        return Result.ok(accountService.deduct(userId, amount));
    }

    /** 查余额：GET /accounts/balance?userId=1 */
    @GetMapping("/balance")
    public Result<BigDecimal> balance(@RequestParam Long userId) {
        return Result.ok(accountService.getBalance(userId));
    }
}
