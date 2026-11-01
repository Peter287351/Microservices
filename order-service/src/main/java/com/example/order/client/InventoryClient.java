package com.example.order.client;

import com.example.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * inventory-service 的 Feign 客户端（五关进阶新增服务，端口 8084）。
 *
 * TODO [Stage-1]: 补齐 deductStock 的注解参数（对照 UserClient 的写法——
 *                 @RequestParam 必须显式写名字，接口编译后参数名会丢）。
 * TODO [Stage-4]: 本接口将加 fallbackFactory 做降级（先留空，Stage 4 回来）。
 */
@FeignClient(name = "inventory-service")
public interface InventoryClient {

    /**
     * 扣减库存。requestId 为幂等键（Stage 2 起生效：同一次业务动作恒定不变）。
     */
    @PostMapping("/inventory/deduct")
    Result<Integer> deductStock(@RequestParam("skuCode") String skuCode,
                                @RequestParam("num") Integer num,
                                @RequestParam("requestId") String requestId);

    /** 查库存（Stage 1 联调用） */
    @PostMapping("/inventory/stock")
    Result<Integer> getStock(@RequestParam("skuCode") String skuCode);
}
