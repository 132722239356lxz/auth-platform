-- ============================================================
--  修复脚本: 将 localhost 替换为 127.0.0.1
--  原因: 单点登录(SSO)跳转时 redirect_uri 需使用 127.0.0.1
--        而非 localhost，否则 OAuth2 授权码回调可能出现问题
--  执行方式: 在 MySQL 客户端中执行本脚本
--  数据库: auth_platform
-- ============================================================
USE `auth_platform`;

-- 1. 修复 oauth2_client_subsystem 表的 redirect_uri 字段
UPDATE `oauth2_client_subsystem`
SET `redirect_uri` = REPLACE(`redirect_uri`, 'http://localhost:', 'http://127.0.0.1:')
WHERE `redirect_uri` LIKE 'http://localhost:%';

-- 2. 修复 oauth2_registered_client 表的 redirect_uris 字段（JSON 格式）
UPDATE `oauth2_registered_client`
SET `redirect_uris` = REPLACE(`redirect_uris`, '"http://localhost:', '"http://127.0.0.1:')
WHERE `redirect_uris` LIKE '%"http://localhost:%';

-- 3. 修复 oauth2_registered_client 表的 post_logout_redirect_uris 字段（JSON 格式）
UPDATE `oauth2_registered_client`
SET `post_logout_redirect_uris` = REPLACE(`post_logout_redirect_uris`, '"http://localhost:', '"http://127.0.0.1:')
WHERE `post_logout_redirect_uris` LIKE '%"http://localhost:%';

-- ============================================================
-- 验证修复结果
-- ============================================================
SELECT client_id, redirect_uri FROM `oauth2_client_subsystem` WHERE redirect_uri IS NOT NULL;
SELECT client_id, redirect_uris, post_logout_redirect_uris FROM `oauth2_registered_client`
WHERE redirect_uris LIKE '%127.0.0.1%' OR post_logout_redirect_uris LIKE '%127.0.0.1%';

SELECT '修复完成！请检查以上结果确认 localhost 已全部替换为 127.0.0.1' AS message;
