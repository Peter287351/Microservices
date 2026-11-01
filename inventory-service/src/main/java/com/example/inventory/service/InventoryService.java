package com.example.inventory.service;

import java.math.BigDecimal;

/**
 * 库存业务接口（Stage 1 查询 + Stage 2 幂等扣减）。
 */
public interface InventoryService {

    /** 查询某 SKU 当前库存（Stage 1 的第一个接口） */
    int getStock(String skuCode);

    /**
     * 扣减库存（Stage 2 实现）。
     *
     * @param skuCode   SKU 编码
     * @param num       扣减数量
     * @param requestId 幂等键（调用方生成，同一次业务动作恒定不变）
     * @return 扣减后剩余库存
     */
    int deduct(String skuCode, int num, String requestId);
}
