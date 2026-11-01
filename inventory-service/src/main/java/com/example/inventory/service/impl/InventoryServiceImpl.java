package com.example.inventory.service.impl;

import com.example.common.api.BusinessException;
import com.example.common.api.ErrorCode;
import com.example.inventory.entity.Inventory;
import com.example.inventory.mapper.InventoryMapper;
import com.example.inventory.service.InventoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 库存业务实现。
 * Stage 1：只实现 getStock（读）；deduct 留 TODO 给 Stage 2。
 * Stage 2：deduct 要完成三层幂等（防重表唯一索引 → 原子条件扣减 → 状态机/CAS），详见关卡手册。
 */
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryMapper inventoryMapper;

    @Override
    public int getStock(String skuCode) {
        Inventory inventory = inventoryMapper.selectOne(
                new LambdaQueryWrapper<Inventory>().eq(Inventory::getSkuCode, skuCode));
        if (inventory == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "SKU 不存在: " + skuCode);
        }
        return inventory.getStockNum();
    }

    @Override
    public int deduct(String skuCode, int num, String requestId) {
        // TODO [Stage-2]: 三层幂等扣减（按关卡手册 02 的任务清单逐层实现）
        //  层次建议：
        //   ① 防重表插入（DuplicateKeyException → 按 status 返回"处理中/已成功"）
        //   ② 原子条件扣减（inventoryMapper.deductStock，affected=0 → 库存不足/无此SKU）
        //   ③ 思考：②成功后防重表 status 怎么流转？事务边界怎么画（结合 Stage 3 红线）？
        throw new UnsupportedOperationException("Stage 2 实现");
    }
}
