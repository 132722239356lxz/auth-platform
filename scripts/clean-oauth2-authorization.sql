-- =====================================================================
-- 清理 oauth2_authorization 表中包含 JwtAuthenticationToken 的历史记录
-- 
-- 背景: 之前 Order(1) 安全链配置了 oauth2ResourceServer，导致 /oauth2/authorize
--       流程中 Principal 为 JwtAuthenticationToken，该类型无法被 SAS 的
--       AllowlistTypeIdResolver 正确反序列化，导致 /oauth2/token 读取时抛异常。
-- 
-- 执行方式: 连接到对应数据库后执行此脚本
-- =====================================================================

-- 1. 查看当前有多少条记录（确认数据量）
SELECT COUNT(*) AS total_records FROM oauth2_authorization;

-- 2. 清理包含 JwtAuthenticationToken 的历史记录
--    SAS 的 attributes 字段以 JSON 存储，包含 JwtAuthenticationToken 的记录会
--    在 /oauth2/token 时触发 InvalidDefinitionException
DELETE FROM oauth2_authorization
WHERE attributes LIKE '%JwtAuthenticationToken%';

-- 3. 清理已过期的 token 记录（可选，释放存储空间）
DELETE FROM oauth2_authorization
WHERE access_token_expires_at < NOW();

-- 4. 确认清理后的记录数
SELECT COUNT(*) AS remaining_records FROM oauth2_authorization;
