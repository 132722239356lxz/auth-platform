-- ============================================================
--  授权门户中台 - 全量种子数据初始化脚本
--  执行顺序: 先建表(schema.sql) → 再执行本脚本(seed data)
--  BCrypt 密码生成方式: BCryptUtil.encode("yourpassword")
-- ============================================================

-- ============================================================
--  1. platform 平台用户 (auth-server)
--     密码均为 admin123 的 BCrypt(10) 哈希
-- ============================================================
INSERT IGNORE INTO sys_user (id, username, password, nickname, email, phone, user_type, tenant_id, dept_id, enabled, account_non_expired, account_non_locked, credentials_non_expired) VALUES
-- 超级管理员
(1, 'admin',    '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '超级管理员', 'admin@auth.com',     '13800000001', 'admin',   'default', 1,  1, 1, 1, 1),
-- 普通用户
(2, 'zhangsan', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '张三',       'zhangsan@test.com', '13800000002', 'user',    'default', 10, 1, 1, 1, 1),
(3, 'lisi',     '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '李四',       'lisi@test.com',     '13800000003', 'user',    'default', 11, 1, 1, 1, 1),
-- 服务账号（用于子系统间调用）
(4, 'svc_gateway', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '网关服务账号', 'svc@internal',     NULL,          'service', 'default', 10, 1, 1, 1, 1);

-- ============================================================
--  2. 客户端注册 (oauth2_registered_client)
--     client_secret 支持两种格式 (CryptoManager.decrypt 自动识别)：
--       A. {noop}明文格式 (开发/测试用)：{noop}secret → 解密得到 secret
--       B. AES加密格式 (生产推荐)：通过 ClientManageController API 注册客户端，
--          或使用 CryptoManager.encrypt("secret") 生成替换下面的值
--     ⚠ 下面使用 {noop} 明文格式，CryptoManager 会自动剥离前缀并返回明文
-- ============================================================
INSERT IGNORE INTO oauth2_registered_client (
    id, client_id, client_id_issued_at, client_secret,
    client_secret_expires_at, client_name,
    client_authentication_methods, authorization_grant_types,
    redirect_uris, post_logout_redirect_uris, scopes,
    client_settings, token_settings
) VALUES
-- 管理后台前端 (authorization_code + PKCE)
('550e8400-e29b-41d4-a716-446655440001', 'admin-web',
 NOW(), '{noop}secret',
 NULL, '管理后台前端',
 'client_secret_basic,client_secret_post',
 'authorization_code,refresh_token',
 '[{"uri":"http://127.0.0.1:5173/login/callback","platform":"web","label":"管理后台回调"},{"uri":"http://127.0.0.1:5173/silent-refresh","platform":"web","label":"静默刷新"}]',
 '[{"uri":"http://127.0.0.1:5173","platform":"web","label":"管理后台登出"}]',
 'openid,profile,read,write',
 '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":false}',
 '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.access-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.refresh-token-time-to-live":["java.time.Duration",86400.000000000],"settings.token.reuse-refresh-tokens":true,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"]}'),

-- 子系统A (authorization_code + client_credentials)
('550e8400-e29b-41d4-a716-446655440002', 'subsystem-a',
 NOW(), '{noop}secret',
 NULL, '子系统A-业务系统',
 'client_secret_basic,client_secret_post',
 'authorization_code,client_credentials,refresh_token',
 '[{"uri":"http://subsystem-a.local:8080/login/oauth2/code/auth-platform","platform":"web","label":"子系统A回调"}]',
 NULL,
 'openid,profile,read',
 '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":true}',
 '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.access-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.refresh-token-time-to-live":["java.time.Duration",43200.000000000],"settings.token.reuse-refresh-tokens":false}'),

-- 子系统B (纯 client_credentials 机器间调用)
('550e8400-e29b-41d4-a716-446655440003', 'subsystem-b',
 NOW(), '{noop}secret',
 NULL, '子系统B-数据同步服务',
 'client_secret_post',
 'client_credentials',
 NULL, NULL,
 'read,write',
 '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
 '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.access-token-time-to-live":["java.time.Duration",7200.000000000],"settings.token.refresh-token-time-to-live":["java.time.Duration",0.000000000]}');

-- ============================================================
--  3. 企业信息 (enterprise_info)
-- ============================================================
INSERT IGNORE INTO enterprise_info (id, enterprise_code, enterprise_name, short_name, enterprise_type,
    credit_code, legal_person, contact_name, contact_phone, contact_email, address, status, tenant_id) VALUES
(1, 'ENT-A001', 'XX科技有限公司',     'XX科技',    'ENTERPRISE', '91110000MA00000001', '赵总', '赵经理', '13900000001', 'zhao@ent-a.com',    '北京市朝阳区XX路100号',  'ACTIVE', 'default'),
(2, 'ENT-B002', 'YY教育集团',         'YY教育',    'SCHOOL',     '91110000MA00000002', '钱校长', '钱主任', '13900000002', 'qian@ent-b.com',    '上海市浦东新区XX路200号', 'ACTIVE', 'default'),
(3, 'ENT-G003', 'ZZ市政府服务中心',   'ZZ市政',     'GOVERNMENT', '91110000MA00000003', '孙局长', '孙科长', '13900000003', 'sun@ent-g.gov.cn',  '广州市天河区XX路300号', 'ACTIVE', 'default');

-- ============================================================
--  4. 企业用户关联 (enterprise_user_rel)
-- ============================================================
INSERT IGNORE INTO enterprise_user_rel (id, enterprise_id, user_id, role, status) VALUES
(1, 1, 1, 'ADMIN',  'ACTIVE'),  -- admin → XX科技 管理员
(2, 1, 2, 'MEMBER', 'ACTIVE'),  -- zhangsan → XX科技 成员
(3, 2, 3, 'ADMIN',  'ACTIVE'),  -- lisi → YY教育 管理员
(4, 2, 2, 'MEMBER', 'ACTIVE');  -- zhangsan → YY教育 成员

-- ============================================================
--  5. 用户-角色关联 (sys_user_role, system-server)
--     ⚠ 确保 system-server/schema.sql 中的角色已先插入
-- ============================================================
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
(1, 1),   -- admin → ROLE_ADMIN
(2, 2),   -- zhangsan → ROLE_USER
(3, 2);   -- lisi → ROLE_USER

-- ============================================================
--  6. 工作流定义种子数据 (auth-flow)
--     实际表结构为 wf_definition + wf_node(关系表), 二者需配套插入
-- ============================================================
INSERT IGNORE INTO wf_definition (id, definition_key, definition_name, description, category, version, status, created_by, create_time) VALUES
(1, 'PERMISSION_APPROVAL', '权限申请审批', '用户申请角色/菜单权限的标准审批流程，支持串行多级审批', 'PERMISSION', 1, 1, 'admin', NOW()),
(2, 'RESOURCE_APPROVAL',  '资源访问申请', '申请访问特定子系统或API资源的审批流程', 'RESOURCE', 1, 1, 'admin', NOW());

-- 权限申请审批节点: 发起 → 部门负责人 → 系统管理员 → 完成
INSERT IGNORE INTO wf_node (definition_id, node_name, node_type, approver_strategy, approvers, sort_order) VALUES
(1, '发起申请',      'START',     'SPECIFIC', NULL,     0),
(1, '部门负责人审批', 'APPROVAL', 'SPECIFIC', 'admin', 1),
(1, '系统管理员审批', 'APPROVAL', 'SPECIFIC', 'admin', 2),
(1, '审批完成',      'END',       'SPECIFIC', NULL,     3);

-- 资源访问申请节点: 发起 → 资源Owner → 完成
INSERT IGNORE INTO wf_node (definition_id, node_name, node_type, approver_strategy, approvers, sort_order) VALUES
(2, '发起申请',   'START',     'SPECIFIC', NULL,     0),
(2, '资源Owner审批', 'APPROVAL', 'SPECIFIC', 'admin', 1),
(2, '完成',       'END',       'SPECIFIC', NULL,     2);

-- ============================================================
--  7. 部门种子数据 (sys_dept)
-- ============================================================
INSERT IGNORE INTO sys_dept (id, parent_id, dept_name, dept_code, leader, phone, email, sort_order, enabled) VALUES
(1,  0, '总公司',           'DEPT_ROOT',    '赵总',   '13800001001', 'ceo@company.com',     1, 1),
(10, 1, '技术研发中心',      'DEPT_TECH',    '张三',   '13800001002', 'tech@company.com',     1, 1),
(11, 1, '市场营销中心',      'DEPT_MARKET',  '李四',   '13800001003', 'market@company.com',   2, 1),
(12, 1, '综合管理部',        'DEPT_ADMIN',   '王五',   '13800001004', 'admin@company.com',   3, 1),
(100,10, '前端开发组',       'DEPT_FRONTEND','赵六',   '13800001005', 'fe@company.com',      1, 1),
(101,10, '后端开发组',       'DEPT_BACKEND', '钱七',   '13800001006', 'be@company.com',      2, 1),
(102,10, '测试组',          'DEPT_QA',      '孙八',   '13800001007', 'qa@company.com',      3, 1);

-- ============================================================
--  8. 字典类型种子数据 (sys_dict_type)
-- ============================================================
INSERT IGNORE INTO sys_dict_type (id, dict_name, dict_type, description, enabled) VALUES
(1, '用户状态',     'sys_user_status',     '用户启用/禁用状态',                1),
(2, '用户类型',     'sys_user_type',       '用户类型(admin/user/service)',    1),
(3, '菜单类型',     'sys_menu_type',       '菜单类型(目录/菜单/按钮)',         1),
(4, '性别',         'sys_user_sex',        '用户性别',                        1),
(5, '企业类型',     'sys_enterprise_type', '企业类型分类',                     1),
(6, '角色状态',     'sys_role_status',     '角色启用/禁用状态',               1),
(7, '账号状态',     'sys_account_status',  '账号状态(NORMAL/LOCKED/EXPIRED)', 1),
(8, '认证方式',     'sys_auth_type',       '登录认证方式(密码/短信/扫码等)',   1);

-- ============================================================
--  9. 字典数据种子数据 (sys_dict_data)
-- ============================================================
INSERT IGNORE INTO sys_dict_data (id, type_id, dict_label, dict_value, sort_order, css_class, enabled, remark) VALUES
-- 用户状态 (type_id=1)
(1,  1, '启用', '1', 1, 'success', 1, NULL),
(2,  1, '禁用', '0', 2, 'danger',  1, NULL),
-- 用户类型 (type_id=2)
(3,  2, '超级管理员', 'admin',   1, 'warning', 1, NULL),
(4,  2, '普通用户',   'user',    2, 'primary', 1, NULL),
(5,  2, '服务账号',   'service', 3, 'info',    1, NULL),
-- 菜单类型 (type_id=3)
(6,  3, '目录', '0', 1, 'warning', 1, NULL),
(7,  3, '菜单', '1', 2, 'primary', 1, NULL),
(8,  3, '按钮', '2', 3, 'success', 1, NULL),
-- 性别 (type_id=4)
(9,  4, '男',   '1', 1, NULL, 1, NULL),
(10, 4, '女',   '2', 2, NULL, 1, NULL),
(11, 4, '未知', '0', 3, NULL, 1, NULL),
-- 企业类型 (type_id=5)
(12, 5, '企业',    'ENTERPRISE', 1, 'primary', 1, NULL),
(13, 5, '学校',    'SCHOOL',     2, 'success', 1, NULL),
(14, 5, '政府机构', 'GOVERNMENT', 3, 'warning', 1, NULL),
-- 角色状态 (type_id=6)
(15, 6, '启用', '1', 1, 'success', 1, NULL),
(16, 6, '禁用', '0', 2, 'danger',  1, NULL),
-- 账号状态 (type_id=7)
(17, 7, '正常',   'NORMAL',   1, 'success', 1, NULL),
(18, 7, '锁定',   'LOCKED',   2, 'warning', 1, '登录失败次数过多自动锁定'),
(19, 7, '过期',   'EXPIRED',  3, 'info',    1, '账号已过有效期'),
(20, 7, '禁用',   'DISABLED', 4, 'danger',  1, '管理员手动禁用'),
-- 认证方式 (type_id=8)
(21, 8, '密码登录', 'PASSWORD', 1, 'primary', 1, '用户名+密码'),
(22, 8, '短信登录', 'SMS',      2, 'success', 1, '手机号+验证码'),
(23, 8, '扫码登录', 'QRCODE',   3, 'info',    1, '移动端扫码授权'),
(24, 8, 'OAuth2',   'OAUTH2',   4, 'warning', 1, '第三方OAuth2.0登录');

-- ============================================================
-- 10. 扩展菜单种子数据 (sys_menu) — 与 frontend-web/src/router/index.ts 静态路由对齐
-- 说明：schema.sql 已初始化 id 1/10-12/15/100-122/150-152，本段使用 INSERT IGNORE 补充其余菜单
-- 菜单类型: 0=目录 1=菜单 2=按钮
-- ============================================================
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order) VALUES
-- ============================================================
-- 一级目录
-- ============================================================
(1,  0, '系统管理',   0, '/system',   NULL, NULL,              'Setting',   1, 1),
(2,  0, '授权管理',   0, '/auth',     NULL, NULL,              'Key',       2, 1),
(3,  0, '工作流审批', 0, '/workflow', NULL, NULL,              'Tickets',   3, 1),
(4,  0, '消息中心',   0, '/message',  NULL, NULL,              'Bell',      4, 1),
(5,  0, 'AI智能应用', 0, '/ai',       NULL, NULL,              'MagicStick',5, 1),
(6,  0, '日志管理',   0, '/log',      NULL, NULL,              'Files',     6, 1),
-- 独立一级菜单
(7,  0, '系统仪表盘', 1, '/dashboard','dashboard/index', 'system:dashboard:view', 'Odometer', 0, 1),

-- ============================================================
-- 系统管理 /system（schema.sql 已有 1/10-12/15/100-122/150-152，以下为补充）
-- ============================================================
(13,  1, '部门管理', 1, '/system/dept',   'system/dept/index',   'system:dept:list',   'OfficeBuilding',4, 1),
(130, 13,'部门新增', 2, NULL,             NULL,                  'system:dept:add',    NULL,     1),
(131, 13,'部门编辑', 2, NULL,             NULL,                  'system:dept:edit',   NULL,     2),
(132, 13,'部门删除', 2, NULL,             NULL,                  'system:dept:delete', NULL,     3),

(14,  1, '数据字典', 1, '/system/dict',   'system/dict/index',   'system:dict:list',   'Document', 5, 1),
(140, 14,'字典新增', 2, NULL,             NULL,                  'system:dict:add',    NULL,     1),
(141, 14,'字典编辑', 2, NULL,             NULL,                  'system:dict:edit',   NULL,     2),
(142, 14,'字典删除', 2, NULL,             NULL,                  'system:dict:delete', NULL,     3),

-- ============================================================
-- 授权管理 /auth
-- ============================================================
(20, 2, '客户端管理',   1, '/auth/client',   'auth/client/index',   'system:client:list',    'Connection', 1, 1),
(21, 2, '授权审计',     1, '/auth/audit',    'auth/audit/index',    'system:audit:list',     'Document',   2, 1),
(22, 2, '子系统Token',  1, '/auth/subsystem','auth/subsystem/index','system:subsystem:list', 'Link',       3, 1),

(200, 20, '客户端新增', 2, NULL, NULL, 'system:client:add',    NULL, 1, 1),
(201, 20, '客户端编辑', 2, NULL, NULL, 'system:client:edit',   NULL, 2, 1),
(202, 20, '客户端删除', 2, NULL, NULL, 'system:client:delete', NULL, 3, 1),
(203, 20, '密钥重置',   2, NULL, NULL, 'system:client:secret', NULL, 4, 1),
(210, 21, '强制吊销',   2, NULL, NULL, 'system:audit:revoke',  NULL, 1, 1),
(220, 22, 'Token记录',  2, NULL, NULL, 'system:subsystem:list',   NULL, 1, 1),
(221, 22, 'Token刷新',  2, NULL, NULL, 'system:subsystem:refresh',NULL, 2, 1),
(222, 22, 'Token吊销',  2, NULL, NULL, 'system:subsystem:revoke', NULL, 3, 1),
(223, 22, '新增令牌',   2, NULL, NULL, 'system:subsystem:add',     NULL, 4, 1),

-- ============================================================
-- 工作流审批 /workflow
-- ============================================================
(30, 3, '审批列表', 1, '/workflow/list',       'workflow/list/index',       'workflow:instance:list',  'List',   1, 1),
(31, 3, '发起申请', 1, '/workflow/create',     'workflow/create/index',     'workflow:instance:add',   'EditPen',2, 1),
(32, 3, '流程定义', 1, '/workflow/definition', 'workflow/definition/index', 'workflow:definition:list','SetUp',   3, 1),

(320, 32, '定义新增', 2, NULL, NULL, 'workflow:definition:add',    NULL, 1, 1),
(321, 32, '定义编辑', 2, NULL, NULL, 'workflow:definition:edit',   NULL, 2, 1),
(322, 32, '定义删除', 2, NULL, NULL, 'workflow:definition:delete', NULL, 3, 1),

-- 审批列表按钮
(300, 30, '审批处理', 2, NULL, NULL, 'workflow:instance:approve', NULL, 1, 1),

-- 表单维护菜单（表单与审批流程绑定）
(33, 3, '表单维护', 1, '/workflow/form', 'workflow/form/index', 'workflow:form:list', 'Document', 4, 1),

-- 表单维护按钮
(330, 33, '表单新增', 2, NULL, NULL, 'workflow:form:add',    NULL, 1, 1),
(331, 33, '表单编辑', 2, NULL, NULL, 'workflow:form:edit',   NULL, 2, 1),
(332, 33, '表单删除', 2, NULL, NULL, 'workflow:form:delete', NULL, 3, 1),

-- ============================================================
-- 消息中心 /message
-- ============================================================
(40, 4, '收件箱',     1, '/message/inbox',    'message/inbox/index',   'message:inbox',         'Message',   1, 1),
(41, 4, '发送消息',   1, '/message/send',     'message/send/index',    'message:send',          'Promotion', 2, 1),
(42, 4, '消息模板',   1, '/message/template', 'message/template/index','message:template:list', 'Document',  3, 1),

-- 发送消息按钮
(410, 41, '消息发送', 2, NULL, NULL, 'message:send',     NULL, 1, 1),
(411, 41, '消息广播', 2, NULL, NULL, 'message:broadcast',NULL, 2, 1),

-- 消息模板按钮
(420, 42, '模板新增', 2, NULL, NULL, 'message:template:add',    NULL, 1, 1),
(421, 42, '模板编辑', 2, NULL, NULL, 'message:template:edit',   NULL, 2, 1),
(422, 42, '模板删除', 2, NULL, NULL, 'message:template:delete', NULL, 3, 1),

-- 消息中心补充菜单（路由已存在，补齐菜单权限）
(43, 4, '发送记录', 1, '/message/record', 'message/record/index', 'message:record:list', 'DocumentCopy', 4, 1),
(44, 4, '子系统事件', 1, '/message/event', 'message/event/index', 'message:event:list', 'Link', 5, 1),
(45, 4, '异常反馈', 1, '/message/incident', 'message/incident/index', 'message:incident:list', 'Warning', 6, 1),

-- 异常反馈按钮
(450, 45, '反馈新增', 2, NULL, NULL, 'message:incident:add',    NULL, 1, 1),
(451, 45, '反馈编辑', 2, NULL, NULL, 'message:incident:edit',   NULL, 2, 1),
(452, 45, '反馈删除', 2, NULL, NULL, 'message:incident:delete', NULL, 3, 1),
(453, 45, '反馈处理', 2, NULL, NULL, 'message:incident:resolve',NULL, 4, 1),
(454, 45, '反馈上报', 2, NULL, NULL, 'message:incident:report', NULL, 5, 1),

-- 消息中心其它按钮
(460, 40, '删除消息', 2, NULL, NULL, 'message:delete',  NULL, 1, 1),
(461, 40, '消息详情', 2, NULL, NULL, 'message:detail',  NULL, 2, 1),

-- ============================================================
-- AI智能应用 /ai
-- ============================================================
(50, 5, '智能搜索',   1, '/ai/search',    'ai/search/index',    'ai:search:list',    'Search',    1, 1),
(51, 5, '知识库管理', 1, '/ai/knowledge', 'ai/knowledge/index', 'ai:knowledge:list', 'Collection',2, 1),
(52, 5, '数据预警',   1, '/ai/analysis',  'ai/analysis/index',  'ai:analysis:list',  'Warning',   3, 1),
(53, 5, 'AI助手',     1, '/ai/chat',      'ai/chat/index',      'ai:chat:list',      'ChatDotRound', 4, 1),

-- 智能搜索按钮
(500, 50, '搜索执行', 2, NULL, NULL, 'ai:search:execute', NULL, 1, 1),

-- 知识库管理按钮
(510, 51, '知识库新增', 2, NULL, NULL, 'ai:knowledge:add',      NULL, 1, 1),
(511, 51, '知识库编辑', 2, NULL, NULL, 'ai:knowledge:edit',     NULL, 2, 1),
(512, 51, '知识库删除', 2, NULL, NULL, 'ai:knowledge:delete',   NULL, 3, 1),
(513, 51, '文档上传',   2, NULL, NULL, 'ai:knowledge:doc:upload',  NULL, 4, 1),
(514, 51, '文档删除',   2, NULL, NULL, 'ai:knowledge:doc:delete', NULL, 5, 1),
(515, 51, '分块查询',   2, NULL, NULL, 'ai:knowledge:chunk:query', NULL, 6, 1),

-- AI助手按钮
(530, 53, '智能对话',   2, NULL, NULL, 'ai:chat:send',        NULL, 1, 1),
(531, 53, '会话新增',   2, NULL, NULL, 'ai:chat:session:add',  NULL, 2, 1),
(532, 53, '会话删除',   2, NULL, NULL, 'ai:chat:session:delete',NULL, 3, 1),
(533, 53, '会话清空',   2, NULL, NULL, 'ai:chat:session:clear', NULL, 4, 1),
(534, 53, '会话列表',   2, NULL, NULL, 'ai:chat:session:list', NULL, 5, 1),
(535, 53, '会话编辑',   2, NULL, NULL, 'ai:chat:session:edit', NULL, 6, 1),

-- 数据预警按钮
(520, 52, '预警列表',   2, NULL, NULL, 'ai:analysis:list',    NULL, 1, 1),
(521, 52, '预警新增',   2, NULL, NULL, 'ai:analysis:add',     NULL, 2, 1),
(522, 52, '预警编辑',   2, NULL, NULL, 'ai:analysis:edit',    NULL, 3, 1),
(523, 52, '预警删除',   2, NULL, NULL, 'ai:analysis:delete',  NULL, 4, 1),
(524, 52, '预警处理',   2, NULL, NULL, 'ai:analysis:resolve', NULL, 5, 1),
(525, 52, '预警查询',   2, NULL, NULL, 'ai:analysis:query',   NULL, 6, 1),
(526, 52, '预警触发',   2, NULL, NULL, 'ai:analysis:trigger', NULL, 7, 1),

-- ============================================================
-- 日志管理 /log
-- ============================================================
(60, 6, '日志查询',    1, '/log/list',     'log/list/index',     'log:list',         'Document', 1, 1),
(61, 6, 'AI日志分析',  1, '/log/analysis', 'log/analysis/index', 'log:analysis:list','Monitor',  2, 1);

-- 日志查询按钮
(600, 60, '清理日志', 2, NULL, NULL, 'log:delete', NULL, 1, 1);

-- AI日志分析按钮
(610, 61, '方案编辑', 2, NULL, NULL, 'log:analysis:edit', NULL, 1, 1);

-- ============================================================
-- 11. 系统公告种子数据 (sys_notice, system-server)
--     notice_type: ANNOUNCEMENT=普通公告 NOTICE=通知公告 WARNING=警告公告
--     priority:    1=普通 2=重要 3=紧急
-- ============================================================
INSERT IGNORE INTO sys_notice (id, title, content, notice_type, priority, publisher_id, publisher_name, top, publish_time, expire_time, enabled, read_count) VALUES
(1, '系统上线公告',
 '尊敬的用户：<br/>授权中台 V2.0 版本已正式上线，本次升级包含以下核心功能：<br/>1. 支持 OAuth2.0 多客户端统一管理<br/>2. 新增子系统 Token 生命周期追踪<br/>3. 完善 RBAC 权限模型，支持菜单/按钮级权限<br/>如遇问题请联系管理员。',
 'ANNOUNCEMENT', 3, 1, '超级管理员', 1,
 '2026-07-01 09:00:00', '2026-08-01 23:59:59', 1, 328),

(2, '安全漏洞修复通知',
 '各位管理员：<br/>平台于2026年7月5日完成了安全漏洞修复，修复内容包括：<br/>1. CVE-2026-XXXX 跨站脚本漏洞<br/>2. Token 泄露风险加固（增加 Token 脱敏存储）<br/>3. 登录接口防暴力破解策略优化<br/>请各子系统及时更新 SDK 版本至 v2.0.1。',
 'WARNING', 3, 1, '超级管理员', 1,
 '2026-07-05 14:00:00', NULL, 1, 156),

(3, '用户操作规范公告',
 '为确保平台数据安全与合规使用，请所有用户遵守以下规范：<br/>1. 禁止将个人账号转借他人使用<br/>2. 角色权限严格按"最小必要"原则分配<br/>3. 发现异常登录请立即上报管理员<br/>4. 敏感操作（如吊销 Token、删除用户）需二次确认<br/>感谢配合！',
 'NOTICE', 2, 1, '超级管理员', 0,
 '2026-07-08 10:00:00', '2026-12-31 23:59:59', 1, 89),

(4, '本周维护通知',
 '各位用户：<br/>平台计划于本周六（7月12日）凌晨 02:00-04:00 进行例行维护，届时服务将短暂中断，预计影响时长不超过30分钟。<br/>维护内容：数据库性能优化、日志归档清理。<br/>请提前做好业务安排，如有紧急事项请联系值班管理员（13800000001）。',
 'NOTICE', 2, 1, '超级管理员', 1,
 '2026-07-10 08:30:00', '2026-07-13 04:00:00', 1, 412),

(5, '新成员入职指引',
 '欢迎新同事加入！<br/>以下是授权中台使用快速指引：<br/>1. 登录系统后前往「系统管理-用户管理」确认个人信息<br/>2. 在「授权审计」页面查看所拥有的 Token 与授权记录<br/>3. 如需申请新角色或接口权限，请在「工作流管理」中提交审批<br/>4. 子系统接入请参考 API 文档（docs/api-reference.md）<br/>遇到问题可在公告下方留言或联系管理员。',
 'ANNOUNCEMENT', 1, 1, '超级管理员', 0,
 '2026-07-12 09:00:00', NULL, 1, 23);

-- ============================================================
-- 12. 角色-菜单关联(管理员=全部 / 普通用户=仅查看菜单，不含按钮)
-- ============================================================
-- 管理员角色：拥有全部菜单、目录、按钮
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu;

-- 普通用户角色：仅可访问目录与菜单页面（不含按钮），保留所有模块查看权限
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 2, id FROM sys_menu WHERE menu_type IN (0, 1);

-- ============================================================
-- 13. AI 供应商配置 (sys_ai_provider)
--     api_key / secret_key 支持两种格式 (CryptoManager.decrypt 自动识别)：
--       A. {noop}明文格式 (开发/测试用)：{noop}sk-xxx → 解密得到 sk-xxx
--       B. AES加密格式 (生产推荐)：通过 AiProviderController API 创建或更新，
--          或使用 CryptoManager.encrypt("secret") 生成替换下面的值
--     enabled: 1=启用 0=禁用
-- ============================================================
INSERT IGNORE INTO sys_ai_provider (id, provider_code, provider_name, provider_type, base_url, api_key, default_model, models, is_primary, priority, timeout_ms, max_retries, temperature, enabled, remark) VALUES

-- 1. DeepSeek - 性价比之王，深度思考能力强（主供应商）
(1, 'deepseek', 'DeepSeek', 'openai',
 'https://api.deepseek.com/v1',
 '{noop}sk-your-deepseek-api-key',
 'deepseek-chat',
 '["deepseek-chat","deepseek-reasoner"]',
 1, 0,
 60000, 3, 0.70, 1,
 '国产高性价比大模型，支持深度推理与通用对话。API Key 请替换为实际密钥。'),

-- 2. OpenAI - 国际主流（备用，priority=1）
(2, 'openai', 'OpenAI', 'openai',
 'https://api.openai.com/v1',
 '{noop}sk-your-openai-api-key',
 'gpt-4o-mini',
 '["gpt-4o","gpt-4o-mini","gpt-4-turbo","gpt-4","o3-mini"]',
 0, 1,
 30000, 3, 0.70, 0,
 'OpenAI 官方模型，GPT-4o 系列能力最强。境外服务，注意网络与数据合规。'),

-- 3. 通义千问 - 阿里云 DashScope（备用，priority=2）
(3, 'qwen', '通义千问', 'openai',
 'https://dashscope.aliyuncs.com/compatible-mode/v1',
 '{noop}sk-your-dashscope-api-key',
 'qwen-plus',
 '["qwen-max","qwen-plus","qwen-turbo","qwen3-235b-a22b"]',
 0, 2,
 30000, 3, 0.70, 1,
 '阿里云通义千问，兼容 OpenAI 接口。国内合规，中文能力强。'),

-- 4. Ollama - 本地私有化部署（备用，priority=3）
(4, 'ollama', 'Ollama (本地)', 'ollama',
 'http://localhost:11434/v1',
 '{noop}ollama',
 'qwen2.5:7b',
 '["qwen2.5:7b","qwen2.5:14b","qwen2.5:32b","llama3:8b","deepseek-r1:8b","deepseek-r1:14b"]',
 0, 3,
 120000, 2, 0.70, 0,
 '本地 Ollama 部署。无需 API Key，数据不出网。先启动服务并 ollama pull 模型。'),

-- 5. Moonshot (Kimi) - 长文本专家（备用，priority=4）
(5, 'moonshot', 'Moonshot AI', 'openai',
 'https://api.moonshot.cn/v1',
 '{noop}sk-your-moonshot-api-key',
 'moonshot-v1-8k',
 '["moonshot-v1-8k","moonshot-v1-32k","moonshot-v1-128k","kimi-latest"]',
 0, 4,
 60000, 3, 0.30, 1,
 '擅长长文本处理，最高 128K 上下文。适合文档分析、合同审查等场景。'),

-- 6. Azure OpenAI - 企业级合规（备用，priority=5）
(6, 'azure-openai', 'Azure OpenAI', 'azure',
 'https://your-resource.openai.azure.com/openai/deployments/gpt-4o',
 '{noop}your-azure-api-key',
 'gpt-4o',
 '["gpt-4o","gpt-4o-mini","gpt-4"]',
 0, 5,
 30000, 3, 0.70, 0,
 '微软 Azure 托管的 OpenAI 服务。企业级 SLA，数据不出境，适合合规场景。'),

-- 7. 智谱 AI (GLM) - 国产多模态（备用，priority=6）
(7, 'zhipu', '智谱 AI (GLM)', 'openai',
 'https://open.bigmodel.cn/api/paas/v4',
 '{noop}your-zhipu-api-key',
 'glm-4-flash',
 '["glm-4-plus","glm-4-flash","glm-4-air","glm-4v-plus"]',
 0, 6,
 30000, 3, 0.70, 0,
 '智谱 AI GLM 系列，支持多模态与工具调用。国产合规，中文理解优秀。'),

-- 8. Anthropic Claude - 安全对齐（备用，priority=7）
(8, 'anthropic', 'Anthropic Claude', 'anthropic',
 'https://api.anthropic.com/v1',
 '{noop}sk-ant-your-anthropic-api-key',
 'claude-sonnet-4-20250514',
 '["claude-sonnet-4-20250514","claude-opus-4-20250514","claude-3-5-sonnet-20241022"]',
 0, 7,
 60000, 3, 0.70, 0,
 'Anthropic Claude 系列，安全对齐与长上下文见长。境外服务，注意合规。');

-- ============================================================
-- 14. AI 复杂度路由配置 (sys_ai_complexity_routing)
--     一个供应商一条路由，定义简单/中等/复杂三种场景使用的模型和最大 tokens
-- ============================================================
INSERT IGNORE INTO sys_ai_complexity_routing (id, routing_name, provider_id, enabled, simple_model, medium_model, complex_model, simple_max_tokens, medium_max_tokens, complex_max_tokens, remark) VALUES

-- DeepSeek：简单用 deepseek-chat，复杂用 deepseek-reasoner
(1, 'DeepSeek 路由', 1, 1,
 'deepseek-chat', 'deepseek-chat', 'deepseek-reasoner',
 1024, 4096, 8192,
 '简单/中等任务用 V3/Chat，复杂推理切 Reasoner'),

-- 通义千问：按任务升档
(2, '通义千问路由', 3, 1,
 'qwen-turbo', 'qwen-plus', 'qwen-max',
 1024, 4096, 8192,
 '简单用 Turbo 省成本，复杂用 Max 保证质量'),

-- Moonshot：利用不同上下文窗口
(3, 'Moonshot 路由', 5, 1,
 'moonshot-v1-8k', 'moonshot-v1-32k', 'moonshot-v1-128k',
 1024, 4096, 8192,
 '按复杂度升档窗口大小，128K 处理超长文档'),

-- OpenAI：简单用小模型降成本（暂不启用）
(4, 'OpenAI 路由', 2, 0,
 'gpt-4o-mini', 'gpt-4o', 'gpt-4o',
 1024, 4096, 8192,
 '简单任务用 mini 省 Token，复杂切换 4o（暂未启用）');

-- ============================================================
-- 15. ✅ 初始化完成
--  ⚠ 注意: 数据库中密码哈希如需使用不同密码，
--          请通过 BCryptUtil.encode("yourPwd") 重新生成后替换。
-- ============================================================
