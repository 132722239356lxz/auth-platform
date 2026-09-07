-- ============================================================
-- migration_v1.8: 补充客户端子系统数据 & 修复 redirect_uris 为空问题
--
-- 背景: v1.6 引入"从 oauth2_client_subsystem 聚合 redirect_uris"机制后，
--       data-seed-all.sql 中的 oauth2_registered_client 初始数据虽包含
--       redirect_uris，但 oauth2_client_subsystem 表没有对应记录。
--       执行 v1.6 迁移后，没有子系统记录的客户端 redirect_uris 被置为 '[]'，
--       导致 Spring Authorization Server 校验 "redirectUris cannot be empty"。
--
-- 本脚本: 为 admin-web / subsystem-a 补充子系统记录，并重新聚合回写。
-- ============================================================
USE `auth_platform`;

-- 提高 GROUP_CONCAT 限制
SET SESSION group_concat_max_len = 1000000;

-- ----------------------------------------------------------
-- 1. 为 admin-web（管理后台）创建子系统记录
--    前端 dev 环境: http://127.0.0.1:5173
--    OAuth2 回调: /login/callback
--    静默刷新: /silent-refresh
-- ----------------------------------------------------------
INSERT IGNORE INTO `oauth2_client_subsystem` (`client_id`, `name`, `icon_url`, `redirect_uri`, `description`, `sort_order`, `visible_portal`, `code`)
VALUES
('admin-web', '管理后台Web端', NULL, 'http://127.0.0.1:5173/login/callback', '管理后台OAuth2回调地址', 1, 1, 'web'),
('admin-web', '管理后台静默刷新', NULL, 'http://127.0.0.1:5173/silent-refresh', '管理后台Token静默刷新回调', 2, 0, 'web');

-- ----------------------------------------------------------
-- 2. 为 subsystem-a 补充子系统记录（如果不存在）
-- ----------------------------------------------------------
INSERT IGNORE INTO `oauth2_client_subsystem` (`client_id`, `name`, `icon_url`, `redirect_uri`, `description`, `sort_order`, `visible_portal`, `code`)
VALUES
('subsystem-a', '子系统A-业务入口', NULL, 'http://subsystem-a.local:8080/login/oauth2/code/auth-platform', '子系统A OAuth2回调地址', 1, 1, 'web');

-- ----------------------------------------------------------
-- 3. 重新聚合 redirect_uris 写回 oauth2_registered_client
--    格式: [{"uri":"...","platform":"<code>","label":"<name>"}]
-- ----------------------------------------------------------
UPDATE oauth2_registered_client c
INNER JOIN (
    SELECT
        client_id,
        CONCAT('[',
            IFNULL(GROUP_CONCAT(
                CONCAT(
                    '{"uri":"', REPLACE(IFNULL(redirect_uri, ''), '"', '\\"'),
                    '","platform":"', IFNULL(code, 'web'),
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
-- 4. 补充 admin-web 的登出回调地址
-- ----------------------------------------------------------
UPDATE oauth2_registered_client
SET post_logout_redirect_uris = '[{"uri":"http://127.0.0.1:5173","platform":"web","label":"管理后台登出"}]'
WHERE client_id = 'admin-web'
  AND (post_logout_redirect_uris IS NULL OR post_logout_redirect_uris = '');

-- ----------------------------------------------------------
-- 5. 兜底：没有子系统但使用了 authorization_code 的客户端
--    redirect_uris 保持为 '[]'（subsystem-b 等 client_credentials 客户端无需回调地址）
-- ----------------------------------------------------------
UPDATE oauth2_registered_client
SET redirect_uris = '[]'
WHERE redirect_uris IS NULL;

-- ============================================================
-- 迁移完成
-- ============================================================
