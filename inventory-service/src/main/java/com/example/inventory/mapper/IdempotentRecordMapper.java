package com.example.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.inventory.entity.IdempotentRecord;

/**
 * 幂等防重表 Mapper（Stage 2 使用）。
 * 插入冲突（DuplicateKeyException）= 重复请求 —— 这是方案②的核心判定。
 */
public interface IdempotentRecordMapper extends BaseMapper<IdempotentRecord> {
}
