package com.example.inventory.controller;

import com.example.common.api.Result;
import com.example.inventory.service.InventoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存接口（order-service 的 Feign 将按服务名 inventory-service 调到这里）。
 * Stage 1：先通查询；Stage 2：接通幂等扣减。
 */
@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /** 查库存：GET /inventory/{skuCode} */
    @GetMapping("/{skuCode}")
    public Result<Integer> stock(@PathVariable String skuCode) {
        return Result.ok(inventoryService.getStock(skuCode));
    }

    /** 扣库存：POST /inventory/deduct?skuCode=SKU-1001&num=1&requestId=xxx（Stage 2 接通幂等） */
    @PostMapping("/deduct")
    public Result<Integer> deduct(@RequestParam String skuCode,
                                  @RequestParam int num,
                                  @RequestParam String requestId) {
        return Result.ok(inventoryService.deduct(skuCode, num, requestId));
    }
}
