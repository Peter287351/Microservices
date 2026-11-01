package com.example.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.inventory.entity.Inventory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface InventoryMapper extends BaseMapper<Inventory> {

    /**
     * TODO [Stage-2]: 原子条件扣减（防超卖的第一道墙）。
     *
     * 手写要求（一条 UPDATE 同时完成"校验+扣减"，杜绝先 SELECT 再 UPDATE 的竞态）：
     *   UPDATE t_inventory
     *   SET stock_num = stock_num - #{num}, updated_at = NOW(6)
     *   WHERE sku_code = #{skuCode} AND stock_num >= #{num}
     *
     * 底层思考点：
     *  1. 为什么必须把"够不够"写进 WHERE 条件，而不是先 SELECT 判断？（行锁 + 原子判定）
     *  2. 返回 affected rows=0 时有哪两种可能？（SKU 不存在 / 库存不足——上层怎么区分？）
     *  3. 这条 SQL 与 @Version 乐观锁（Inventory.version）是什么关系？两者能同时用吗，为什么？
     *
     * 提示：用 @Update 注解或 XML 皆可；本方法返回 int（受影响行数）。
     */
    int deductStock(@Param("skuCode") String skuCode, @Param("num") int num);
}
