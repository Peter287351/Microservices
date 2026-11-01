package com.example.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 库存实体（t_inventory，建表见 docker/mysql/init/02-stage-init.sql）。
 *
 * version 字段 = 乐观锁版本号：MyBatis-Plus 搭配 OptimisticLockerInnerInterceptor 使用，
 * UPDATE 时自动带 WHERE version=? 且 SET version=version+1 —— Stage 2 状态机 CAS 的地基。
 */
@Getter
@Setter
@TableName("t_inventory")
public class Inventory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** SKU 编码，业务唯一键 */
    private String skuCode;

    /** 当前库存数（>=0）；扣减即 -1 的原子 UPDATE，禁止"先查后改" */
    private Integer stockNum;

    /** 乐观锁版本号（Stage 2 CAS 用） */
    @Version
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
