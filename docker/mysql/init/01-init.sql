-- MySQL 首次初始化时自动执行（仅在数据卷为空、第一次建库时运行一次）
-- 说明：业务表(t_user / t_order)由各服务的 JPA 自动创建（ddl-auto: update），
--       这里只负责保证数据库与字符集正确。

CREATE DATABASE IF NOT EXISTS micro
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 模块 09（Seata）时会在每个业务库追加 undo_log 表，届时在此补充。
