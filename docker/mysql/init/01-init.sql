-- MySQL 首次初始化时自动执行（仅在数据卷为空、第一次建库时运行一次）
-- 说明：业务表(t_user / t_order)由各服务的 JPA 自动创建（ddl-auto: update），
--       这里只负责保证数据库与字符集正确。

CREATE DATABASE IF NOT EXISTS micro
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 模块 09（Seata AT 模式）：undo_log 回滚镜像表
-- 现有环境请手动执行一次：docker exec micro-mysql mysql -uroot -proot123 micro -e "<下方 SQL>"
CREATE TABLE IF NOT EXISTS micro.undo_log (
    `branch_id`     BIGINT       NOT NULL COMMENT '分支事务ID',
    `xid`           VARCHAR(128) NOT NULL COMMENT '全局事务ID',
    `context`       VARCHAR(128) NOT NULL COMMENT '上下文（序列化方式等）',
    `rollback_info` LONGBLOB     NOT NULL COMMENT '回滚镜像（前后数据快照）',
    `log_status`    INT          NOT NULL COMMENT '0正常 1防御状态',
    `log_created`   DATETIME(6)  NOT NULL COMMENT '创建时间',
    `log_modified`  DATETIME(6)  NOT NULL COMMENT '修改时间',
    UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Seata AT 模式回滚日志表';

