-- ============================================================
--  授权门户中台 - 菜单数据重建脚本
--  依据：frontend-web/src/router/index.ts 静态路由
--  执行前请确认：当前菜单数据已混乱，本脚本会清空 sys_menu / sys_role_menu 后重建
--  执行顺序：先执行 system-server/schema.sql 建表 → 再执行本脚本
-- ============================================================

-- 1. 清空旧菜单及角色菜单关联
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE sys_role_menu;
TRUNCATE TABLE sys_menu;
SET FOREIGN_KEY_CHECKS = 1;

-- 2. 重新插入菜单数据
-- 菜单类型: 0=目录 1=菜单 2=按钮
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, enabled) VALUES
-- ============================================================
-- 一级目录
-- ============================================================
(1,  0, '系统管理',   0, '/system',   NULL, NULL,              'Setting',   1, 1),
(2,  0, '授权管理',   0, '/auth',     NULL, NULL,              'Key',       2, 1),
(3,  0, '工作流审批', 0, '/workflow', NULL, NULL,              'Tickets',   3, 1),
(4,  0, '消息中心',   0, '/message',  NULL, NULL,              'Bell',      4, 1),
(5,  0, 'AI智能应用', 0, '/ai',       NULL, NULL,              'MagicStick',5, 1),
(6,  0, '日志管理',   0, '/log',      NULL, NULL,              'Files',     6, 1),
-- ============================================================
-- 独立一级菜单（仪表盘）
-- ============================================================
(7,  0, '系统仪表盘', 1, '/dashboard','dashboard/index', 'system:dashboard:view', 'Odometer', 0, 1),

-- ============================================================
-- 系统管理 /system
-- ============================================================
(10, 1, '用户管理', 1, '/system/user',  'system/user/index',  'system:user:list',  'User',          1, 1),
(11, 1, '角色管理', 1, '/system/role',  'system/role/index',  'system:role:list',  'UserFilled',    2, 1),
(12, 1, '菜单管理', 1, '/system/menu',  'system/menu/index',  'system:menu:list',  'Menu',          3, 1),
(13, 1, '部门管理', 1, '/system/dept',  'system/dept/index',  'system:dept:list',  'OfficeBuilding',4, 1),
(14, 1, '数据字典', 1, '/system/dict',  'system/dict/index',  'system:dict:list',  'Document',      5, 1),
(15, 1, '公告管理', 1, '/system/notice','system/notice/index','system:notice:list','Bell',          6, 1),

-- 用户管理按钮
(100, 10, '用户新增', 2, NULL, NULL, 'system:user:add',    NULL, 1, 1),
(101, 10, '用户编辑', 2, NULL, NULL, 'system:user:edit',   NULL, 2, 1),
(102, 10, '用户删除', 2, NULL, NULL, 'system:user:delete', NULL, 3, 1),
-- 角色管理按钮
(110, 11, '角色新增', 2, NULL, NULL, 'system:role:add',    NULL, 1, 1),
(111, 11, '角色编辑', 2, NULL, NULL, 'system:role:edit',   NULL, 2, 1),
(112, 11, '角色删除', 2, NULL, NULL, 'system:role:delete', NULL, 3, 1),
-- 菜单管理按钮
(120, 12, '菜单新增', 2, NULL, NULL, 'system:menu:add',    NULL, 1, 1),
(121, 12, '菜单编辑', 2, NULL, NULL, 'system:menu:edit',   NULL, 2, 1),
(122, 12, '菜单删除', 2, NULL, NULL, 'system:menu:delete', NULL, 3, 1),
-- 部门管理按钮
(130, 13, '部门新增', 2, NULL, NULL, 'system:dept:add',    NULL, 1, 1),
(131, 13, '部门编辑', 2, NULL, NULL, 'system:dept:edit',   NULL, 2, 1),
(132, 13, '部门删除', 2, NULL, NULL, 'system:dept:delete', NULL, 3, 1),
-- 数据字典按钮
(140, 14, '字典新增', 2, NULL, NULL, 'system:dict:add',    NULL, 1, 1),
(141, 14, '字典编辑', 2, NULL, NULL, 'system:dict:edit',   NULL, 2, 1),
(142, 14, '字典删除', 2, NULL, NULL, 'system:dict:delete', NULL, 3, 1),
-- 公告管理按钮
(150, 15, '公告新增', 2, NULL, NULL, 'system:notice:add',    NULL, 1, 1),
(151, 15, '公告编辑', 2, NULL, NULL, 'system:notice:edit',   NULL, 2, 1),
(152, 15, '公告删除', 2, NULL, NULL, 'system:notice:delete', NULL, 3, 1),

-- ============================================================
-- 授权管理 /auth
-- ============================================================
(20, 2, '客户端管理',   1, '/auth/client',   'auth/client/index',   'system:client:list',    'Connection', 1, 1),
(21, 2, '授权审计',     1, '/auth/audit',    'auth/audit/index',    'system:audit:list',     'Document',   2, 1),
(22, 2, '子系统Token',  1, '/auth/subsystem','auth/subsystem/index','system:subsystem:list', 'Link',       3, 1),

-- 客户端管理按钮
(200, 20, '客户端新增', 2, NULL, NULL, 'system:client:add',    NULL, 1, 1),
(201, 20, '客户端编辑', 2, NULL, NULL, 'system:client:edit',   NULL, 2, 1),
(202, 20, '客户端删除', 2, NULL, NULL, 'system:client:delete', NULL, 3, 1),
(203, 20, '密钥重置',   2, NULL, NULL, 'system:client:secret', NULL, 4, 1),
-- 授权审计按钮
(210, 21, '强制吊销',   2, NULL, NULL, 'system:audit:revoke',  NULL, 1, 1),
-- 子系统Token按钮
(220, 22, 'Token记录',  2, NULL, NULL, 'system:subsystem:list',   NULL, 1, 1),
(221, 22, 'Token刷新',  2, NULL, NULL, 'system:subsystem:refresh',NULL, 2, 1),
(222, 22, 'Token吊销',  2, NULL, NULL, 'system:subsystem:revoke', NULL, 3, 1),

-- ============================================================
-- 工作流审批 /workflow
-- ============================================================
(30, 3, '审批列表', 1, '/workflow/list',       'workflow/list/index',       'workflow:instance:list',  'List',   1, 1),
(31, 3, '发起申请', 1, '/workflow/create',     'workflow/create/index',     'workflow:instance:add',   'EditPen',2, 1),
(32, 3, '流程定义', 1, '/workflow/definition', 'workflow/definition/index', 'workflow:definition:list','SetUp',   3, 1),

-- 流程定义按钮
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
(460, 40, '删除消息', 2, NULL, NULL, 'message:delete', NULL, 1, 1),
(461, 40, '消息详情', 2, NULL, NULL, 'message:detail', NULL, 2, 1),

-- ============================================================
-- AI智能应用 /ai
-- ============================================================
(50, 5, '智能搜索',   1, '/ai/search',    'ai/search/index',    'ai:search:list',    'Search',    1, 1),
(51, 5, '知识库管理', 1, '/ai/knowledge', 'ai/knowledge/index', 'ai:knowledge:list', 'Collection',2, 1),
(52, 5, '数据预警',   1, '/ai/analysis',  'ai:analysis/index',  'ai:analysis:list',  'Warning',   3, 1),
-- 智能搜索按钮
(500, 50, '搜索执行', 2, NULL, NULL, 'ai:search:execute', NULL, 1, 1),
-- 知识库管理按钮
(510, 51, '知识库新增', 2, NULL, NULL, 'ai:knowledge:add',    NULL, 1, 1),
(511, 51, '知识库编辑', 2, NULL, NULL, 'ai:knowledge:edit',   NULL, 2, 1),
(512, 51, '知识库删除', 2, NULL, NULL, 'ai:knowledge:delete', NULL, 3, 1),
(513, 51, '文档上传',   2, NULL, NULL, 'ai:knowledge:doc:upload',  NULL, 4, 1),
(514, 51, '文档删除',   2, NULL, NULL, 'ai:knowledge:doc:delete',  NULL, 5, 1),
(515, 51, '分块查询',   2, NULL, NULL, 'ai:knowledge:chunk:query', NULL, 6, 1),
-- 数据预警按钮
(520, 52, '预警查询', 2, NULL, NULL, 'ai:analysis:list',    NULL, 1, 1),
(521, 52, '预警新增', 2, NULL, NULL, 'ai:analysis:add',     NULL, 2, 1),
(522, 52, '预警编辑', 2, NULL, NULL, 'ai:analysis:edit',    NULL, 3, 1),
(523, 52, '预警删除', 2, NULL, NULL, 'ai:analysis:delete',  NULL, 4, 1),
(524, 52, '预警处理', 2, NULL, NULL, 'ai:analysis:resolve', NULL, 5, 1),
(525, 52, '预警查询', 2, NULL, NULL, 'ai:analysis:query',   NULL, 6, 1),
(526, 52, '预警触发', 2, NULL, NULL, 'ai:analysis:trigger', NULL, 7, 1),
-- AI助手
(53, 5, 'AI助手',     1, '/ai/chat',    'ai/chat/index',   'ai:chat:list',       'ChatDotRound', 4, 1),
-- AI助手按钮
(530, 53, '发送消息', 2, NULL, NULL, 'ai:chat:send',           NULL, 1, 1),
(531, 53, '会话新增', 2, NULL, NULL, 'ai:chat:session:add',    NULL, 2, 1),
(532, 53, '会话删除', 2, NULL, NULL, 'ai:chat:session:delete', NULL, 3, 1),
(533, 53, '清空会话', 2, NULL, NULL, 'ai:chat:session:clear',  NULL, 4, 1),
(534, 53, '会话列表', 2, NULL, NULL, 'ai:chat:session:list',   NULL, 5, 1),
(535, 53, '会话编辑', 2, NULL, NULL, 'ai:chat:session:edit',   NULL, 6, 1),

-- ============================================================
-- 日志管理 /log
-- ============================================================
(60, 6, '日志查询',    1, '/log/list',     'log/list/index',     'log:list',         'Document', 1, 1),
(61, 6, 'AI日志分析',  1, '/log/analysis', 'log/analysis/index', 'log:analysis:list','Monitor',  2, 1),

-- 日志查询按钮
(600, 60, '清理日志', 2, NULL, NULL, 'log:delete', NULL, 1, 1),

-- AI日志分析按钮
(610, 61, '方案编辑', 2, NULL, NULL, 'log:analysis:edit', NULL, 1, 1),

-- ============================================================
-- AI配置 /system/ai-config（系统管理子目录，仅管理员）
-- 说明：复杂度路由已整合到供应商配置页面，不再单独配置菜单
-- ============================================================
(80, 8, '供应商配置',  1, '/system/provider',   'system/provider',     'system:ai-provider:list', 'Connection', 1, 1),
(81, 8, '调用记录',    1, '/system/invoke-log', 'system/invoke-log',   'system:ai-invoke-log:list', 'Document', 2, 1),

-- 供应商配置按钮
(800, 80, '供应商新增', 2, NULL, NULL, 'system:ai-provider:add',    NULL, 1, 1),
(801, 80, '供应商编辑', 2, NULL, NULL, 'system:ai-provider:edit',   NULL, 2, 1),
(802, 80, '供应商删除', 2, NULL, NULL, 'system:ai-provider:delete', NULL, 3, 1),
(803, 80, '供应商测试', 2, NULL, NULL, 'system:ai-provider:test',   NULL, 4, 1),
(804, 80, '路由配置',   2, NULL, NULL, 'system:ai-routing:edit',    NULL, 5, 1),
-- 调用记录按钮
(810, 81, '记录详情',   2, NULL, NULL, 'system:ai-invoke-log:query',  NULL, 1, 1),
(811, 81, '记录删除',   2, NULL, NULL, 'system:ai-invoke-log:delete', NULL, 2, 1),
(812, 81, '记录清理',   2, NULL, NULL, 'system:ai-invoke-log:clear',  NULL, 3, 1);

-- 3. 角色-菜单关联
-- 管理员角色 (role_id=1)：拥有全部菜单和按钮
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu;

-- 普通用户角色 (role_id=2)：仅查看菜单（不含按钮），保留仪表盘、系统管理查看（不含 AI 配置）、授权管理查看、工作流查看
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, id FROM sys_menu
WHERE menu_type IN (0, 1)
  AND id IN (1,2,3,4,5,6,7, 10,11,12,13,14,15, 20,21,22, 30,31,32, 40,41,42, 50,51,52,53, 60,61);
