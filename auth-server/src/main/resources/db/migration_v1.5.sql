USE `auth_platform`;

-- ====================================================================
-- migration_v1.5: 客户端多子系统改造
-- 一个客户端可配置多个子系统，子系统独立管理图标、URL、描述等
-- ====================================================================

-- 1. 新建客户端子系统表 oauth2_client_subsystem
CREATE TABLE IF NOT EXISTS `oauth2_client_subsystem` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `client_id`      VARCHAR(100)  NOT NULL COMMENT '客户端ID(关联 oauth2_registered_client.client_id)',
    `name`           VARCHAR(100)  NOT NULL COMMENT '子系统名称',
    `icon_url`       VARCHAR(500)  DEFAULT NULL COMMENT '图标图片URL',
    `redirect_uri`   VARCHAR(500)  DEFAULT NULL COMMENT '回调URL(子系统访问入口域名)',
    `description`    VARCHAR(255)  DEFAULT NULL COMMENT '子系统描述',
    `sort_order`     INT           DEFAULT 0  COMMENT '门户排序号',
    `visible_portal` TINYINT(1)    DEFAULT 1  COMMENT '是否在门户展示',
    `create_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_client_id` (`client_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户端子系统配置表(一对多)';

-- 2. 将 oauth2_client_profile 中的旧数据迁移为子系统记录
--    每个客户端如果有画像数据，则迁移为一条子系统记录
INSERT INTO `oauth2_client_subsystem` (`client_id`, `name`, `icon_url`, `redirect_uri`, `description`, `sort_order`, `visible_portal`)
SELECT
    p.client_id,
    c.client_name,
    p.icon,
    p.base_url,
    p.description,
    COALESCE(p.sort_order, 0),
    COALESCE(p.visible_portal, 1)
FROM `oauth2_client_profile` p
INNER JOIN `oauth2_registered_client` c ON p.client_id = c.client_id
LEFT JOIN `oauth2_client_subsystem` s ON p.client_id = s.client_id
WHERE s.id IS NULL;

-- 3. oauth2_client_profile 表保留（不再写入新数据，仅作历史参考）

-- 迁移完成
