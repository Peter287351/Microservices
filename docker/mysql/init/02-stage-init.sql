-- =====================================================================
-- 五关进阶训练 · Stage 2/3 表结构（库存/幂等防重/本地消息）
-- 注意：
--   · 全新数据卷（首次 up）会自动执行；现有数据卷需手动执行一次：
--     docker exec -i micro-mysql mysql -uroot -proot123 micro < docker/mysql/init/02-stage-init.sql
--   · t_user/t_order/t_account/undo_log 由 JPA 与 01-init.sql 负责，本文件不重复
-- =====================================================================

-- 库存表（Stage 2：原子条件扣减 + 乐观锁 CAS）
CREATE TABLE IF NOT EXISTS `t_inventory` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `sku_code`   VARCHAR(64)  NOT NULL COMMENT 'SKU 编码',
  `stock_num`  INT          NOT NULL DEFAULT 0 COMMENT '当前库存数',
  `version`    INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_code` (`sku_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '库存';

-- 幂等防重表（Stage 2 方案②：唯一索引兜底；插得进去=第一次，冲突=重复请求）
CREATE TABLE IF NOT EXISTS `t_idempotent_record` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `idempotent_key` VARCHAR(128) NOT NULL COMMENT '幂等键（requestId 等）',
  `biz_type`       VARCHAR(32)  NOT NULL COMMENT '业务类型 DEDUCT/CREATE_ORDER...',
  `status`         TINYINT      NOT NULL DEFAULT 0 COMMENT '0处理中 1已成功',
  `created_at`     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key_type` (`idempotent_key`, `biz_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '幂等防重';

-- 本地消息表（Stage 3：业务与消息同事务落库 → 异步投递 → 对账兜底）
CREATE TABLE IF NOT EXISTS `t_local_message` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `message_id`    VARCHAR(64)  NOT NULL COMMENT '消息唯一标识（消费端幂等键）',
  `topic`         VARCHAR(64)  NOT NULL COMMENT '投递目标 Topic',
  `payload`       TEXT         NOT NULL COMMENT '消息体 JSON',
  `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0待发送 1已发送 2死信(超限)',
  `retry_count`   INT          NOT NULL DEFAULT 0,
  `next_retry_at` DATETIME(6)  NULL COMMENT '下次重试时间（退避）',
  `created_at`    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at`    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_id` (`message_id`),
  KEY `idx_status_retry` (`status`, `next_retry_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '本地消息表';

-- 种子数据：毕业典礼纪念款 SKU（幂等种子，可重复执行）
INSERT INTO `t_inventory` (`sku_code`, `stock_num`)
SELECT 'SKU-1001', 100
WHERE NOT EXISTS (SELECT 1 FROM `t_inventory` WHERE `sku_code` = 'SKU-1001');
