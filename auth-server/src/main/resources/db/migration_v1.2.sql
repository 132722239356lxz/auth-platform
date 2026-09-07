-- ============================================================
-- auth_platform 数据库迁移脚本 v1.2
-- 新增: 用户-子系统可见性关系表
-- 在 auth_platform 数据库中执行
-- ============================================================
USE `auth_platform`;

-- ----------------------------------------------------------
-- 1. 用户-子系统可见性关系表 sys_user_subsystem
--    维护用户对子系统的可见性（门户用户能看到哪些子系统）
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user_subsystem` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`      BIGINT       NOT NULL COMMENT '门户用户ID(sys_user.id)',
    `client_id`    VARCHAR(100) NOT NULL COMMENT '子系统客户端ID(oauth2_registered_client.client_id)',
    `visible`      TINYINT(1)   DEFAULT 1 COMMENT '是否可见: 1=可见 0=隐藏',
    `granted_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
    `granted_by`   BIGINT       DEFAULT NULL COMMENT '授权人ID(NULL=系统自动)',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_client` (`user_id`, `client_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_client_id` (`client_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-子系统可见性关系表';

-- ============================================================
-- 迁移完成
-- 后续可通过 /api/system/user-subsystems 接口管理用户-子系统关系
-- ============================================================
