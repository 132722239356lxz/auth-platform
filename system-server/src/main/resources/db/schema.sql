-- ============================================
-- 系统管理模块 数据库初始化SQL
-- 角色/菜单/权限/部门/字典 RBAC模型
-- ============================================

-- 角色表
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色主键',
    `role_code`   VARCHAR(50)  NOT NULL COMMENT '角色编码(如ROLE_ADMIN)',
    `role_name`   VARCHAR(50)  NOT NULL COMMENT '角色名称(如系统管理员)',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '角色描述',
    `sort_order`  INT          DEFAULT 0  COMMENT '排序号',
    `enabled`     TINYINT(1)   DEFAULT 1  COMMENT '状态: 1=启用 0=禁用',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 菜单表(支持目录/菜单/按钮三级)
CREATE TABLE IF NOT EXISTS `sys_menu` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '菜单主键',
    `parent_id`   BIGINT       DEFAULT 0  COMMENT '父菜单ID(0=顶级)',
    `menu_name`   VARCHAR(50)  NOT NULL COMMENT '菜单名称',
    `menu_type`   TINYINT      NOT NULL COMMENT '菜单类型: 0=目录 1=菜单 2=按钮',
    `path`        VARCHAR(200) DEFAULT NULL COMMENT '路由路径',
    `component`   VARCHAR(200) DEFAULT NULL COMMENT '前端组件路径',
    `permission`  VARCHAR(100) DEFAULT NULL COMMENT '权限标识(按钮类型必填, 如system:user:list)',
    `icon`        VARCHAR(50)  DEFAULT NULL COMMENT '菜单图标',
    `sort_order`  INT          DEFAULT 0  COMMENT '排序号',
    `enabled`     TINYINT(1)   DEFAULT 1  COMMENT '状态: 1=启用 0=禁用',
    `is_frame`    TINYINT(1)   DEFAULT 0  COMMENT '是否外链: 1=是 0=否',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单表';

-- 角色-菜单关联表(N:N)
CREATE TABLE IF NOT EXISTS `sys_role_menu` (
    `id`      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
    UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`),
    KEY `idx_role_id` (`role_id`),
    KEY `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-菜单关联表';

-- 用户-角色关联表(N:N)
CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id`      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联表';

-- 部门表(支持多级树形结构)
CREATE TABLE IF NOT EXISTS `sys_dept` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '部门主键',
    `parent_id`   BIGINT       DEFAULT 0  COMMENT '父部门ID(0=顶级)',
    `dept_name`   VARCHAR(50)  NOT NULL COMMENT '部门名称',
    `dept_code`   VARCHAR(50)  NOT NULL COMMENT '部门编码(唯一)',
    `leader`      VARCHAR(50)  DEFAULT NULL COMMENT '负责人',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '联系电话',
    `email`       VARCHAR(50)  DEFAULT NULL COMMENT '邮箱',
    `sort_order`  INT          DEFAULT 0  COMMENT '排序号',
    `enabled`     TINYINT(1)   DEFAULT 1  COMMENT '状态: 1=启用 0=禁用',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_dept_code` (`dept_code`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

-- 兼容: 用户表添加部门字段(如由 auth-server 建表则通过 ALTER 追加)
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS dept_id BIGINT DEFAULT NULL COMMENT '部门ID';

-- 兼容: 用户表添加头像字段
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS avatar TEXT DEFAULT NULL COMMENT '头像URL或Base64';

-- 兼容历史数据库: 将头像字段扩长
ALTER TABLE sys_user MODIFY COLUMN avatar TEXT COMMENT '头像URL或Base64';

-- 字典类型表
CREATE TABLE IF NOT EXISTS `sys_dict_type` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '字典类型主键',
    `dict_name`   VARCHAR(50)  NOT NULL COMMENT '字典名称',
    `dict_type`   VARCHAR(50)  NOT NULL COMMENT '字典类型(唯一标识)',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '描述',
    `enabled`     TINYINT(1)   DEFAULT 1  COMMENT '状态: 1=启用 0=禁用',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型表';

-- 字典数据表
CREATE TABLE IF NOT EXISTS `sys_dict_data` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '字典数据主键',
    `type_id`     BIGINT       NOT NULL COMMENT '字典类型ID',
    `dict_label`  VARCHAR(50)  NOT NULL COMMENT '字典标签(显示名称)',
    `dict_value`  VARCHAR(50)  NOT NULL COMMENT '字典值',
    `sort_order`  INT          DEFAULT 0  COMMENT '排序号',
    `css_class`   VARCHAR(50)  DEFAULT NULL COMMENT 'CSS样式类',
    `list_class`  VARCHAR(50)  DEFAULT NULL COMMENT '列表样式类',
    `enabled`     TINYINT(1)   DEFAULT 1  COMMENT '状态: 1=启用 0=禁用',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY `idx_type_id` (`type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典数据表';

-- 系统公告表
CREATE TABLE IF NOT EXISTS `sys_notice` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '公告主键',
    `title`         VARCHAR(100) NOT NULL COMMENT '公告标题',
    `content`       TEXT         NOT NULL COMMENT '公告内容',
    `notice_type`   VARCHAR(50)  DEFAULT 'ANNOUNCEMENT' COMMENT '公告类型',
    `priority`      INT          DEFAULT 1 COMMENT '优先级：1普通 2重要 3紧急',
    `publisher_id`  BIGINT       DEFAULT NULL COMMENT '发布人ID',
    `publisher_name` VARCHAR(50)  DEFAULT NULL COMMENT '发布人名称',
    `top`           TINYINT(1)   DEFAULT 0 COMMENT '是否置顶：1=是 0=否',
    `publish_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    `expire_time`   DATETIME     DEFAULT NULL COMMENT '过期时间',
    `enabled`       TINYINT(1)   DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
    `read_count`    BIGINT       DEFAULT 0 COMMENT '阅读次数',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统公告表';

-- ============================================
-- 用户-应用(客户端)关联表
-- 记录门户用户对各子系统/应用的可见性与授权关系
-- ============================================
CREATE TABLE IF NOT EXISTS `sys_user_subsystem` (
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id`      BIGINT       NOT NULL COMMENT '用户ID',
    `client_id`    VARCHAR(100) NOT NULL COMMENT '客户端ID(oauth2_registered_client.client_id)',
    `visible`      TINYINT(1)   DEFAULT 1 COMMENT '是否可见: 1=可见 0=不可见',
    `granted_by`   BIGINT       DEFAULT NULL COMMENT '授权人ID',
    `granted_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
    UNIQUE KEY `uk_user_client` (`user_id`, `client_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_client_id` (`client_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-应用关联表(控制用户可见哪些子系统)';

-- ============================================
-- 初始化基础数据(可选)
-- ============================================

-- 默认管理员角色
INSERT IGNORE INTO `sys_role` (`id`, `role_code`, `role_name`, `description`, `sort_order`) VALUES
(1, 'ROLE_ADMIN', '系统管理员', '拥有系统所有权限', 1),
(2, 'ROLE_USER',  '普通用户',   '基础访问权限', 2);

-- ============================================================
-- 菜单初始化（id 固定，便于角色关联与前端路由对应）
-- 约定：
--   1. menu_type: 0=目录 1=菜单 2=按钮
--   2. path:      与前端路由真实 path 保持一致（如 /system/user）
--   3. component: 与前端组件路径保持一致（如 system/user.vue）
--   4. permission:与前端 meta.permission 保持一致；目录/无校验菜单留 NULL
--   5. 前端为静态路由，本表是"菜单管理"与后端 @RequirePermission 的权威来源
-- ============================================================

-- 一级目录：系统管理
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`) VALUES
(1,  0, '系统管理', 0, '/system',       NULL,                 NULL,              'Setting',   1);

-- 二级菜单 + 按钮：系统管理
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`) VALUES
(10, 1, '用户管理', 1, '/system/user',     'system/user.vue',     'system:user:list',     'User',        1),
(100, 10, '用户新增', 2, NULL, NULL, 'system:user:add',          NULL, 1),
(101, 10, '用户编辑', 2, NULL, NULL, 'system:user:edit',         NULL, 2),
(102, 10, '用户删除', 2, NULL, NULL, 'system:user:delete',       NULL, 3),
(11, 1, '角色管理', 1, '/system/role',     'system/role.vue',     'system:role:list',     'UserFilled',   2),
(110, 11, '角色新增', 2, NULL, NULL, 'system:role:add',          NULL, 1),
(111, 11, '角色编辑', 2, NULL, NULL, 'system:role:edit',         NULL, 2),
(112, 11, '角色删除', 2, NULL, NULL, 'system:role:delete',       NULL, 3),
(12, 1, '菜单管理', 1, '/system/menu',     'system/menu.vue',     'system:menu:list',     'Menu',        3),
(120, 12, '菜单新增', 2, NULL, NULL, 'system:menu:add',          NULL, 1),
(121, 12, '菜单编辑', 2, NULL, NULL, 'system:menu:edit',         NULL, 2),
(122, 12, '菜单删除', 2, NULL, NULL, 'system:menu:delete',       NULL, 3),
(15, 1, '公告管理', 1, '/system/notice',   'system/notice.vue',   'system:notice:list',  'Bell',        9),
(150, 15, '公告新增', 2, NULL, NULL, 'system:notice:add',        NULL, 1),
(151, 15, '公告编辑', 2, NULL, NULL, 'system:notice:edit',       NULL, 2),
(152, 15, '公告删除', 2, NULL, NULL, 'system:notice:delete',     NULL, 3),
(13, 1, '部门管理', 1, '/system/dept',     'system/dept.vue',     'system:dept:list',    'OfficeBuilding', 4),
(130, 13, '部门新增', 2, NULL, NULL, 'system:dept:add',          NULL, 1),
(131, 13, '部门编辑', 2, NULL, NULL, 'system:dept:edit',         NULL, 2),
(132, 13, '部门删除', 2, NULL, NULL, 'system:dept:delete',       NULL, 3),
(14, 1, '数据字典', 1, '/system/dict',     'system/dict.vue',     'system:dict:list',    'Document',    5);

-- 一级目录：认证授权（auth-server 相关）
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`) VALUES
(2,  0, '认证授权', 0, '/auth',          NULL,                 NULL,              'Lock',      2);

-- 二级菜单 + 按钮：认证授权
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`) VALUES
(21, 2, '客户端管理', 1, '/auth/client',    'auth/client.vue',    NULL, 'Key',         1),
(22, 2, '访问审计', 1, '/auth/audit',     'auth/audit.vue',     NULL, 'Document',    2),
(23, 2, '子系统管理', 1, '/auth/subsystem', 'auth/subsystem.vue', NULL, 'Connection',  3);

-- 一级目录：工作流（auth-flow）
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`) VALUES
(3,  0, '工作流', 0, '/workflow',          NULL,                 NULL,              'SetUp',     3);

-- 二级菜单 + 按钮：工作流
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`) VALUES
(31, 3, '流程定义', 1, '/workflow/definition', 'workflow/definition.vue', 'workflow:definition:list', 'SetUp',     1),
(310, 31, '定义新增', 2, NULL, NULL, 'workflow:definition:add',    NULL, 1),
(311, 31, '定义编辑', 2, NULL, NULL, 'workflow:definition:edit',   NULL, 2),
(312, 31, '定义删除', 2, NULL, NULL, 'workflow:definition:delete', NULL, 3),
(32, 3, '审批列表', 1, '/workflow/list',       'workflow/list.vue',       NULL, 'List',       2),
(33, 3, '发起申请', 1, '/workflow/create',     'workflow/create.vue',     NULL, 'Edit',       3),
(34, 3, '审批详情', 1, '/workflow/detail/:id', 'workflow/detail.vue',     NULL, 'View',       4);

-- 一级目录：消息中心（auth-message）
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`) VALUES
(4,  0,  '消息中心', 0, '/message', NULL, NULL, 'Bell', 4);

-- 二级菜单 + 按钮：消息中心（与前端 /message/* 路由一一对应）
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`) VALUES
(41, 4, '收件箱',   1, '/message/inbox',    'message/inbox.vue',    'message:inbox',          'Message',       1),
(410, 41, '消息删除', 2, NULL, NULL, 'message:delete',          NULL, 1),
(42, 4, '发送消息', 1, '/message/send',     'message/send.vue',     'message:send',           'Promotion',     2),
(420, 42, '广播消息', 2, NULL, NULL, 'message:broadcast',       NULL, 1),
(421, 42, '事件推送', 2, NULL, NULL, 'message:event:push',      NULL, 2),
(43, 4, '发送记录', 1, '/message/record',   'message/record.vue',   'message:inbox',          'DocumentCopy',  3),
(44, 4, '消息模板', 1, '/message/template', 'message/template.vue', 'message:template:list',  'Document',      4),
(440, 44, '模板新增', 2, NULL, NULL, 'message:template:add',    NULL, 1),
(441, 44, '模板编辑', 2, NULL, NULL, 'message:template:edit',   NULL, 2),
(442, 44, '模板删除', 2, NULL, NULL, 'message:template:delete', NULL, 3),
(45, 4, '子系统事件', 1, '/message/events',   'message/events.vue',   'message:event:list',    'Connection',    5),
(46, 4, '异常反馈', 1, '/message/incident', 'message/incident.vue', 'message:incident:list',  'WarningFilled', 6),
(460, 46, '异常上报', 2, NULL, NULL, 'message:incident:report', NULL, 1),
(461, 46, '异常处理', 2, NULL, NULL, 'message:incident:resolve',NULL, 2),
(462, 46, '异常删除', 2, NULL, NULL, 'message:incident:delete', NULL, 3);

-- 一级目录：AI 智能应用（ai-agent-server）
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`) VALUES
(5,  0, 'AI智能应用', 0, '/ai',           NULL,                 NULL,              'MagicStick', 5);

-- 二级菜单：AI 智能应用（前端无后端权限校验，permission 留 NULL）
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`) VALUES
(51, 5, '智能搜索',   1, '/ai/search',     'ai/search.vue',     NULL, 'Search',      1),
(52, 5, '知识库管理', 1, '/ai/knowledge', 'ai/knowledge.vue',  NULL, 'Collection',  2),
(53, 5, 'AI助手',     1, '/ai/chat',       'ai/chat.vue',       NULL, 'ChatDotRound',3),
(54, 5, '数据预警',   1, '/ai/analysis',   'ai/analysis.vue',   NULL, 'Warning',     4);

-- 一级目录：日志管理（log-server）
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`) VALUES
(6,  0, '日志管理', 0, '/log',            NULL,                 NULL,              'Files',     6);

-- 二级菜单：日志管理
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`) VALUES
(61, 6, '日志查询',   1, '/log/list',     'log/list.vue',     NULL, 'Document', 1),
(62, 6, 'AI日志分析', 1, '/log/analysis', 'log/analysis.vue', NULL, 'Monitor',  2);

-- 默认管理员角色拥有所有菜单权限（含目录/菜单/按钮）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, id FROM `sys_menu`;

-- 默认普通用户拥有查看权限（仅目录和菜单，无按钮权限）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 2, id FROM `sys_menu` WHERE menu_type IN (0, 1);

-- 受控模块：普通用户不应访问，统一从 role_id=2 撤回
-- （认证授权、工作流、AI 配置、消息中心管理类、日志管理）
DELETE FROM `sys_role_menu` WHERE `role_id` = 2 AND `menu_id` IN (
    2, 21, 22, 23,
    3, 31, 32, 33, 34, 310, 311, 312,
    4, 41, 42, 43, 44, 45, 46, 410, 420, 421, 440, 441, 442, 460, 461, 462,
    5, 51, 52, 53, 54,
    6, 61, 62,
    8, 80, 82, 800, 801, 802, 803, 820, 821, 822
);

-- ============================================
-- AI 配置管理 - 表结构、菜单与权限初始化
-- 注意：本段必须保持在文件末尾。前面存在
--       "INSERT IGNORE INTO sys_role_menu SELECT 2, id FROM sys_menu WHERE menu_type IN (0,1)"
--       会无条件给普通用户授予所有目录+菜单，AI 配置属管理员功能，必须晚于该语句插入。
-- ============================================

-- ----------------------------
-- 1. AI 供应商配置表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_ai_provider` (
    `id`            BIGINT       AUTO_INCREMENT PRIMARY KEY                            COMMENT '主键',
    `provider_code` VARCHAR(64)  NOT NULL                                              COMMENT '供应商编码(唯一, 如 openai/deepseek/qwen)',
    `provider_name` VARCHAR(128) NOT NULL                                              COMMENT '供应商名称',
    `provider_type` VARCHAR(32)  NOT NULL DEFAULT 'openai'                             COMMENT '供应商类型: openai/azure/anthropic/ollama/custom',
    `base_url`      VARCHAR(512) NOT NULL                                              COMMENT 'API 基础地址(如 https://api.openai.com/v1)',
    `api_key`       VARCHAR(512) NOT NULL                                              COMMENT 'API Key(加密存储)',
    `secret_key`    VARCHAR(512)          DEFAULT NULL                                 COMMENT '可选密钥(加密存储)',
    `default_model` VARCHAR(128)          DEFAULT NULL                                 COMMENT '默认模型',
    `embedding_model` VARCHAR(128)        DEFAULT NULL                                 COMMENT '向量模型(用于文本转向量, 如 text-embedding-3-small)',
    `models`        TEXT                  DEFAULT NULL                                 COMMENT '支持的模型列表(JSON 数组)',
    `is_primary`    TINYINT(1)            DEFAULT 0                                    COMMENT '是否主供应商: 1=是 0=否(备用), 系统仅允许一个主供应商',
    `priority`      INT                   DEFAULT 0                                    COMMENT '优先级(数值越小越高, 用于备用供应商的故障切换顺序)',
    `timeout_ms`    INT                   DEFAULT 30000                                COMMENT '请求超时时间(毫秒)',
    `max_retries`   INT                   DEFAULT 3                                    COMMENT '最大重试次数',
    `temperature`   DECIMAL(3,2)          DEFAULT 0.70                                 COMMENT '默认温度参数(0.00-2.00)',
    `enabled`       TINYINT(1)            DEFAULT 1                                    COMMENT '是否启用: 1=启用 0=禁用',
    `remark`        VARCHAR(512)          DEFAULT NULL                                 COMMENT '备注',
    `create_time`   DATETIME              DEFAULT CURRENT_TIMESTAMP                    COMMENT '创建时间',
    `update_time`   DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_provider_code` (`provider_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 供应商配置表';

-- ----------------------------
-- 2. AI 复杂度路由配置表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_ai_complexity_routing` (
    `id`                 BIGINT       AUTO_INCREMENT PRIMARY KEY                       COMMENT '主键',
    `routing_name`       VARCHAR(128) NOT NULL                                         COMMENT '路由配置名称',
    `provider_id`        BIGINT       NOT NULL                                         COMMENT '关联供应商ID',
    `enabled`            TINYINT(1)            DEFAULT 1                               COMMENT '是否启用: 1=启用 0=禁用',
    `simple_model`       VARCHAR(128)          DEFAULT NULL                            COMMENT '简单任务使用的模型',
    `medium_model`       VARCHAR(128)          DEFAULT NULL                            COMMENT '中等任务使用的模型',
    `complex_model`      VARCHAR(128)          DEFAULT NULL                            COMMENT '复杂任务使用的模型',
    `simple_max_tokens`  INT                   DEFAULT 1024                            COMMENT '简单任务最大 tokens',
    `medium_max_tokens`  INT                   DEFAULT 2048                            COMMENT '中等任务最大 tokens',
    `complex_max_tokens` INT                   DEFAULT 4096                            COMMENT '复杂任务最大 tokens',
    `remark`             VARCHAR(512)          DEFAULT NULL                            COMMENT '备注',
    `create_time`        DATETIME              DEFAULT CURRENT_TIMESTAMP               COMMENT '创建时间',
    `update_time`        DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_provider_id` (`provider_id`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 复杂度路由配置表';

-- ----------------------------
-- 2.6 AI 调用记录表(用于排查 AI 生成是否合理、统计 token 与工具/RAG 使用情况)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_ai_invoke_log` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT                           COMMENT '主键',
    `session_id`       VARCHAR(64)  DEFAULT NULL                                       COMMENT '会话ID',
    `user_id`          VARCHAR(64)  DEFAULT NULL                                       COMMENT '用户ID',
    `provider_code`    VARCHAR(64)  DEFAULT NULL                                       COMMENT '实际使用的供应商编码',
    `provider_name`    VARCHAR(128) DEFAULT NULL                                       COMMENT '供应商名称',
    `provider_type`    VARCHAR(32)  DEFAULT NULL                                       COMMENT '供应商类型(openai/azure/ollama等)',
    `model_name`       VARCHAR(128) DEFAULT NULL                                       COMMENT '实际使用的模型',
    `complexity`       VARCHAR(32)  DEFAULT NULL                                       COMMENT '复杂度路由结果(simple/normal/complex)',
    `cached`           TINYINT(1)   DEFAULT 0                                          COMMENT '是否命中缓存: 0=否 1=是',
    `prompt_tokens`    INT          DEFAULT NULL                                       COMMENT '输入 token 数',
    `completion_tokens` INT         DEFAULT NULL                                       COMMENT '输出 token 数',
    `total_tokens`     INT          DEFAULT NULL                                       COMMENT '总 token 数',
    `tool_names`       TEXT         DEFAULT NULL                                       COMMENT '使用的工具名(JSON 数组)',
    `tool_count`       INT          DEFAULT 0                                          COMMENT '工具数量',
    `rag_references`   TEXT         DEFAULT NULL                                       COMMENT 'RAG 引用片段(JSON 数组)',
    `context_content`  MEDIUMTEXT   DEFAULT NULL                                       COMMENT '上下文内容(系统提示/检索上下文摘要, 超长截断)',
    `user_input`       MEDIUMTEXT   DEFAULT NULL                                       COMMENT '用户输入内容',
    `ai_output`        MEDIUMTEXT   DEFAULT NULL                                       COMMENT 'AI 输出内容(超长截断)',
    `elapsed_ms`       INT          DEFAULT NULL                                       COMMENT '调用耗时(毫秒)',
    `success`          TINYINT(1)   DEFAULT 1                                          COMMENT '是否成功: 0=失败 1=成功',
    `error_msg`        VARCHAR(512) DEFAULT NULL                                       COMMENT '错误信息(失败时使用)',
    `invoke_time`      DATETIME     DEFAULT NULL                                       COMMENT '调用时间',
    `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP                          COMMENT '记录创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_session` (`session_id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_provider` (`provider_code`),
    KEY `idx_invoke_time` (`invoke_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 调用记录表';

-- ----------------------------
-- 3. 菜单: AI 配置(目录 8 / 菜单 80-82 / 按钮 800-822)
-- ----------------------------
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`) VALUES
-- AI 配置（挂在 /system 下，与前端路由 /system/provider、/system/invoke-log 保持一致）
-- 说明：复杂度路由(routing) 前端暂无对应页面，暂不建菜单，避免指向不存在的路由
-- 一级目录
(8,   1,  'AI配置',     0, '/system/ai-config',       NULL,                 NULL,                      'Cpu',        10),
-- 二级菜单
(80,  8,  '供应商配置', 1, '/system/provider',    'system/provider.vue',    'system:ai-provider:list', 'Connection', 1),
(82,  8,  '调用记录',   1, '/system/invoke-log', 'system/ai-invoke-log.vue', 'system:ai-invoke-log:list', 'Document', 2),
-- 供应商配置按钮
(800, 80, '供应商新增', 2, NULL, NULL, 'system:ai-provider:add',    NULL, 1),
(801, 80, '供应商编辑', 2, NULL, NULL, 'system:ai-provider:edit',   NULL, 2),
(802, 80, '供应商删除', 2, NULL, NULL, 'system:ai-provider:delete', NULL, 3),
(803, 80, '供应商测试', 2, NULL, NULL, 'system:ai-provider:test',   NULL, 4),
-- 调用记录按钮
(820, 82, '记录详情',   2, NULL, NULL, 'system:ai-invoke-log:query',   NULL, 1),
(821, 82, '记录删除',   2, NULL, NULL, 'system:ai-invoke-log:delete', NULL, 2),
(822, 82, '记录清理',   2, NULL, NULL, 'system:ai-invoke-log:clear',  NULL, 3);

-- AI 配置仅管理员角色(role_id=1) 可见（上方 SELECT 1,id 已覆盖，这里保留显式授权以保证幂等）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, id FROM `sys_menu` WHERE id IN (8, 80, 82, 800, 801, 802, 803, 820, 821, 822);

-- 幂等兜底：本文件二次执行时，上方 "SELECT 2, id ... menu_type IN (0,1)" 会把已存在的
-- 8/80/82 授给普通用户，这里统一撤回（AI 配置属管理员功能）。
DELETE FROM `sys_role_menu` WHERE `role_id` = 2 AND `menu_id` IN (8, 80, 82);