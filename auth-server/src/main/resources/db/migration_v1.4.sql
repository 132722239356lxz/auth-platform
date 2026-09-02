-- ============================================================
-- auth_platform 数据库迁移脚本 v1.4
-- 新增: 客户端门户画像表 oauth2_client_profile
--       替代孤儿表 sys_subsystem，统一管理客户端展示信息
-- ============================================================
USE `auth_platform`;

-- ----------------------------------------------------------
-- 1. 新建客户端门户画像表
--    按 client_id 关联 oauth2_registered_client，维护图标/描述/基础URL/排序/门户可见
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `oauth2_client_profile` (
    `client_id`      VARCHAR(100) NOT NULL COMMENT '客户端ID(关联 oauth2_registered_client.client_id)',
    `icon`           VARCHAR(50)  DEFAULT NULL COMMENT '子系统图标',
    `base_url`       VARCHAR(500) DEFAULT NULL COMMENT '子系统基础URL',
    `description`    VARCHAR(255) DEFAULT NULL COMMENT '子系统描述',
    `sort_order`     INT          DEFAULT 0  COMMENT '门户排序号',
    `visible_portal` TINYINT(1)   DEFAULT 1  COMMENT '是否在门户展示(区分前端子系统与纯M2M客户端)',
    PRIMARY KEY (`client_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户端门户画像(展示信息)';

-- ----------------------------------------------------------
-- 2. 清理 personal-blog 孤儿数据
-- ----------------------------------------------------------
DELETE FROM `sys_user_subsystem` WHERE `client_id` = 'personal-blog';

-- ----------------------------------------------------------
-- 3. 删除孤儿子系统注册表
-- ----------------------------------------------------------
DROP TABLE IF EXISTS `sys_subsystem`;

-- ============================================================
-- 迁移完成
-- ============================================================
