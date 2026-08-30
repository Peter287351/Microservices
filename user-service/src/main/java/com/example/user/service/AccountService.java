package com.example.user.service;

import com.example.common.api.BusinessException;
import com.example.common.api.ErrorCode;
import com.example.user.entity.Account;
import com.example.user.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 账户业务（模块 09）：扣款。
 * 本地 @Transactional 保证单库原子性；是否随下单一起回滚，由全局事务（Seata）决定——
 * 本方法只管"扣钱并留好后悔药（undo_log 由数据源代理自动写）"。
 */
@Service
public class AccountService {

    /** 学习用初始余额：首次扣款自动开户时赠送 */
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("10000");

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public BigDecimal deduct(Long userId, BigDecimal amount) {
        Account account = accountRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Account created = new Account();
                    created.setUserId(userId);
                    created.setBalance(INITIAL_BALANCE);
                    return accountRepository.save(created);
                });

        if (account.getBalance().compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.BALANCE_NOT_ENOUGH);
        }
        account.setBalance(account.getBalance().subtract(amount));
        return accountRepository.save(account).getBalance();
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(Long userId) {
        Account account = accountRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Account created = new Account();
                    created.setUserId(userId);
                    created.setBalance(INITIAL_BALANCE);
                    return accountRepository.save(created);
                });
        return account.getBalance();
    }
}
