package com.example.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 幂等防重表（t_idempotent_record，建表见 02-stage-init.sql）。
 * Stage 2 方案②的载体：业务键唯一索引兜底，插得进去=第一次，冲突=重复请求。
 */
@Getter
@Setter
@TableName("t_idempotent_record")
public class IdempotentRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 幂等键：调用方生成的唯一请求标识（如 requestId = orderId + 扣减序号） */
    private String idempotentKey;

    /** 业务类型（DEDUCT / CREATE_ORDER ...），与 key 组成复合唯一约束 */
    private String bizType;

    /** 状态：0 处理中 / 1 已成功 —— 冲突后据此返回"处理中"或"已成功"（Stage 2 考点） */
    private Integer status;

    private LocalDateTime createdAt;
}
