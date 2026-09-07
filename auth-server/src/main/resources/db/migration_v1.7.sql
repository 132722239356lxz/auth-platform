-- ============================================================
-- migration_v1.7: 子系统增加平台标识 code
--
-- 业务背景:
--   一个客户端下可能挂多个子系统，分别面向不同平台（Web、小程序、App等）。
--   原 oauth2_client_subsystem 没有"平台"维度，redirect_uris 聚合时
--   全部被写成 platform="web"，无法区分数据来源。
--
--   本次新增 code 字段，作为子系统的"平台标识"：
--     web      Web 端（浏览器/管理后台）
--     miniapp  小程序端（微信/支付宝小程序）
--     app      移动 App 端
--     desktop  桌面客户端
--     admin    管理后台
--
--   该 code 同时写入 oauth2_registered_client.redirect_uris 数组中
--   的 platform 字段，便于 Spring Authorization Server 区分。
-- ============================================================

USE `auth_platform`;

-- ----------------------------------------------------------
-- 1. 兼容处理：先删除唯一约束/外键如果未来存在（当前 oauth2_client_subsystem 没有外键，无需处理）
-- ----------------------------------------------------------

-- ----------------------------------------------------------
-- 2. 新增 code 列 + 默认值 web（兼容历史记录）
-- ----------------------------------------------------------
ALTER TABLE `oauth2_client_subsystem`
    ADD COLUMN `code` VARCHAR(32) NOT NULL DEFAULT 'web' COMMENT '平台标识: web/miniapp/app/desktop/admin' AFTER `client_id`,
    ADD INDEX `idx_code` (`code`);

-- ----------------------------------------------------------
-- 3. 重新聚合 redirect_uris 写回 oauth2_registered_client
--    把 subsystem.code 同步到 JSON 中的 platform 字段
-- ----------------------------------------------------------
SET SESSION group_concat_max_len = 1000000;

UPDATE oauth2_registered_client c
INNER JOIN (
    SELECT
        client_id,
        CONCAT('[',
            IFNULL(GROUP_CONCAT(
                CONCAT(
                    '{"uri":"', REPLACE(IFNULL(redirect_uri, ''), '"', '\\"'),
                    '","platform":"', REPLACE(IFNULL(code, 'web'), '"', '\\"'),
                    '","label":"', REPLACE(IFNULL(name, ''), '"', '\\"'), '"}'
                )
                ORDER BY sort_order ASC, id ASC
                SEPARATOR ','
            ), ''),
        ']') AS new_uris
    FROM oauth2_client_subsystem
    WHERE redirect_uri IS NOT NULL AND redirect_uri != ''
    GROUP BY client_id
) s ON c.client_id = s.client_id
SET c.redirect_uris = s.new_uris;

-- ----------------------------------------------------------
-- 4. 没有可用子系统的 client，redirect_uris 写为 '[]'，兜底
-- ----------------------------------------------------------
UPDATE oauth2_registered_client
SET redirect_uris = '[]'
WHERE redirect_uris IS NULL
   OR client_id NOT IN (
       SELECT DISTINCT client_id
       FROM oauth2_client_subsystem
       WHERE redirect_uri IS NOT NULL AND redirect_uri != ''
   );

-- ============================================================
-- 迁移完成
-- oauth2_client_subsystem 新增 code 列，默认值 web
-- oauth2_registered_client.redirect_uris 中 platform 字段同步为 code
-- ============================================================
