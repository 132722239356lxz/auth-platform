-- ============================================================
-- migration_v1.6: 客户端-子系统联动改造
-- 1. oauth2_registered_client.redirect_uris 重新从 oauth2_client_subsystem 聚合
--    保证 Spring Authorization Server 鉴权 + 门户导航读到的回调地址与子系统一致
-- 2. 清理废弃的 oauth2_client_profile（v1.4 创建，新设计不再使用）
-- ============================================================
USE `auth_platform`;

-- 提高 GROUP_CONCAT 长度限制（防止子系统过多时截断）
SET SESSION group_concat_max_len = 1000000;

-- ----------------------------------------------------------
-- 1. 从 oauth2_client_subsystem 聚合 redirect_uris 写回 oauth2_registered_client
--    JSON 格式: [{"uri":"...","platform":"web","label":"<subsystem.name>"}]
-- ----------------------------------------------------------
UPDATE oauth2_registered_client c
INNER JOIN (
    SELECT
        client_id,
        CONCAT('[',
            IFNULL(GROUP_CONCAT(
                CONCAT(
                    '{"uri":"', REPLACE(IFNULL(redirect_uri, ''), '"', '\\"'),
                    '","platform":"web"',
                    ',"label":"', REPLACE(IFNULL(name, ''), '"', '\\"'), '"}'
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
-- 2. 没有可用子系统的 client，redirect_uris 写为 '[]'，避免 Spring Authorization Server 鉴权报错
-- ----------------------------------------------------------
UPDATE oauth2_registered_client
SET redirect_uris = '[]'
WHERE redirect_uris IS NULL
   OR client_id NOT IN (
       SELECT DISTINCT client_id
       FROM oauth2_client_subsystem
       WHERE redirect_uri IS NOT NULL AND redirect_uri != ''
   );

-- ----------------------------------------------------------
-- 3. 清理废弃的 oauth2_client_profile（v1.4 创建，新设计不再使用）
--    数据已在 v1.5 迁移到 oauth2_client_subsystem，删除该表不影响业务
-- ----------------------------------------------------------
DROP TABLE IF EXISTS `oauth2_client_profile`;

-- ----------------------------------------------------------
-- 4. 兜底清理 v1.2 中 personal-blog 孤儿记录（幂等操作）
-- ----------------------------------------------------------
DELETE FROM `sys_user_subsystem` WHERE `client_id` = 'personal-blog';

-- ============================================================
-- 迁移完成
-- 客户端不再单独维护回调地址，由 oauth2_client_subsystem 统一提供
-- 后端 Oauth2ClientService 会持续保持二者一致
-- ============================================================
