-- ============================================================
-- auth_platform 数据库迁移脚本 v1.3
-- 改动: redirect_uris/post_logout_redirect_uris 扩展为 TEXT 类型，
--       存储格式从逗号分隔改为 JSON 数组 [{"uri":"...","platform":"...","label":"..."}]
-- ============================================================
USE `auth_platform`;

-- 1. 扩展列类型 varchar(1000) → TEXT（支持更长的 JSON 数据）
ALTER TABLE `oauth2_registered_client`
    MODIFY COLUMN `redirect_uris` TEXT DEFAULT NULL COMMENT '回调地址(JSON数组, 含uri/platform/label)',
    MODIFY COLUMN `post_logout_redirect_uris` TEXT DEFAULT NULL COMMENT '登出回调地址(JSON数组, 含uri/platform/label)';

-- 2. 将现有逗号分隔数据迁移为 JSON 数组格式
--    旧格式: http://a.com/cb,http://b.com/cb
--    新格式: [{"uri":"http://a.com/cb","platform":"web"},{"uri":"http://b.com/cb","platform":"web"}]
--    注意: 此 UPDATE 仅处理非NULL且非JSON格式的旧数据
UPDATE `oauth2_registered_client`
SET `redirect_uris` = CONCAT(
    '[',
    (SELECT GROUP_CONCAT(
        CONCAT('{"uri":"', TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(redirect_uris, ',', numbers.n), ',', -1)),
               '","platform":"web"}')
        ORDER BY numbers.n
        SEPARATOR ',')
     FROM (
         SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
         UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
     ) numbers
     WHERE CHAR_LENGTH(redirect_uris) - CHAR_LENGTH(REPLACE(redirect_uris, ',', '')) >= numbers.n - 1
    ),
    ']'
)
WHERE `redirect_uris` IS NOT NULL
  AND `redirect_uris` != ''
  AND `redirect_uris` NOT LIKE '[%';

UPDATE `oauth2_registered_client`
SET `post_logout_redirect_uris` = CONCAT(
    '[',
    (SELECT GROUP_CONCAT(
        CONCAT('{"uri":"', TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(post_logout_redirect_uris, ',', numbers.n), ',', -1)),
               '","platform":"web"}')
        ORDER BY numbers.n
        SEPARATOR ',')
     FROM (
         SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
         UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
     ) numbers
     WHERE CHAR_LENGTH(post_logout_redirect_uris) - CHAR_LENGTH(REPLACE(post_logout_redirect_uris, ',', '')) >= numbers.n - 1
    ),
    ']'
)
WHERE `post_logout_redirect_uris` IS NOT NULL
  AND `post_logout_redirect_uris` != ''
  AND `post_logout_redirect_uris` NOT LIKE '[%';

-- ============================================================
-- 迁移完成
-- ============================================================
