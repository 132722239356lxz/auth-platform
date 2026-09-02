    -- ============================================================
    -- 审批流数据库表结构 (支持串行/并行/条件分支)
    -- ============================================================

    -- 工作流定义(模板)
    CREATE TABLE IF NOT EXISTS wf_definition (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        definition_key VARCHAR(100) NOT NULL COMMENT '流程定义Key',
        definition_name VARCHAR(200) NOT NULL COMMENT '流程名称',
        description VARCHAR(500) COMMENT '描述',
        category VARCHAR(50) DEFAULT 'PERMISSION' COMMENT '分类: PERMISSION/RESOURCE/ROLE',
        version INT DEFAULT 1 COMMENT '版本号',
        status TINYINT DEFAULT 1 COMMENT '状态: 0-草稿 1-启用 2-停用',
        created_by VARCHAR(100) COMMENT '创建人',
        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        UNIQUE KEY uk_def_key_ver (definition_key, version)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流定义';

    -- 审批节点 (支持串行/并行/条件表达式)
    CREATE TABLE IF NOT EXISTS wf_node (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        definition_id BIGINT NOT NULL COMMENT '所属流程定义ID',
        node_name VARCHAR(200) NOT NULL COMMENT '节点名称',
        node_type VARCHAR(30) DEFAULT 'APPROVAL' COMMENT 'START/APPROVAL/CONDITION/PARALLEL_START/PARALLEL_END/CALLBACK/END',
        exec_mode VARCHAR(20) DEFAULT 'SERIAL' COMMENT '执行模式: SERIAL(串行)/PARALLEL(并行)',
        parallel_group VARCHAR(100) COMMENT '并行分组标识',
        parent_node_id BIGINT COMMENT '父节点ID(并行子节点指向PARALLEL_START的ID)',
        condition_expression VARCHAR(500) COMMENT '条件表达式(SpEL格式)',
        on_condition_fail VARCHAR(20) DEFAULT 'REJECT' COMMENT '条件不满足时: REJECT/SKIP',
        approver_strategy VARCHAR(50) DEFAULT 'SPECIFIC' COMMENT '审批人策略',
        approvers VARCHAR(500) COMMENT '指定审批人',
        approver_role VARCHAR(100) COMMENT '审批角色',
        sort_order INT DEFAULT 0 COMMENT '排序号',
        timeout_hours INT DEFAULT 0 COMMENT '超时时间(小时),0=不限时',
        countersign TINYINT(1) DEFAULT 0 COMMENT '会签模式',
        reject_strategy VARCHAR(20) DEFAULT 'TO_PREV' COMMENT '驳回策略',
        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
        KEY idx_def_id (definition_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批节点';

    -- 流程实例
    CREATE TABLE IF NOT EXISTS wf_instance (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        definition_id BIGINT NOT NULL COMMENT '流程定义ID',
        title VARCHAR(200) NOT NULL COMMENT '流程标题',
        applicant VARCHAR(100) NOT NULL COMMENT '申请人',
        apply_content TEXT COMMENT '申请内容(JSON)',
        status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/WITHDRAWN',
        current_node_id BIGINT COMMENT '当前节点ID',
        current_approver VARCHAR(500) COMMENT '当前审批人',
        approval_chain VARCHAR(1000) COMMENT '审批链(节点ID逗号分隔)',
        approval_records TEXT COMMENT '审批记录(JSON数组)',
        created_by VARCHAR(100),
        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        finish_time DATETIME COMMENT '完成时间',
        KEY idx_applicant (applicant),
        KEY idx_definition_id (definition_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例';

    -- 审批任务
    CREATE TABLE IF NOT EXISTS wf_task (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        instance_id BIGINT NOT NULL COMMENT '流程实例ID',
        node_id BIGINT NOT NULL COMMENT '节点ID',
        node_name VARCHAR(200) COMMENT '节点名称(冗余)',
        approver VARCHAR(100) NOT NULL COMMENT '审批人',
        status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/TRANSFERRED',
        comment VARCHAR(500) COMMENT '审批意见',
        approve_time DATETIME COMMENT '审批时间',
        transferred_from VARCHAR(100) COMMENT '转交来源',
        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        KEY idx_instance (instance_id),
        KEY idx_approver_status (approver, status),
        KEY idx_node (node_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批任务';

    -- 审批表单定义（一个表单绑定一个流程定义definition_key，schema描述前端渲染字段）
    CREATE TABLE IF NOT EXISTS wf_form (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        form_key VARCHAR(100) NOT NULL COMMENT '表单唯一标识',
        form_name VARCHAR(200) NOT NULL COMMENT '表单名称',
        definition_key VARCHAR(100) NOT NULL COMMENT '绑定的流程定义Key',
        schema_json TEXT NOT NULL COMMENT '表单字段Schema(JSON数组)',
        icon VARCHAR(100) COMMENT '图标class或标识',
        sort_order INT DEFAULT 0 COMMENT '排序号',
        status TINYINT DEFAULT 1 COMMENT '状态: 0-停用 1-启用',
        apply_type VARCHAR(50) COMMENT '业务场景类型: SUBSYSTEM_VISIBILITY / ROLE 等',
        created_by VARCHAR(100) COMMENT '创建人',
        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        UNIQUE KEY uk_form_key (form_key)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批表单定义';



    -- ============================================================
    -- 审批流完整测试数据
    -- 测试用户: admin/admin123(超级管理员) zhangsan/123456(申请人) lisi/123456(部门经理) wangwu/123456(总监)
    -- 兼容 data-seed-all.sql 中的定义1/2，使用 INSERT IGNORE 安全重复执行
    -- ============================================================

    -- ============================================================
    -- Part 1: 流程定义 (共6个，覆盖串行/并行/条件分支/会签)
    -- ============================================================

    INSERT IGNORE INTO wf_definition (id, definition_key, definition_name, description, category, version, status, created_by, create_time, update_time) VALUES
    (1, 'PERMISSION_APPROVAL', '权限申请审批',   '员工申请角色/菜单权限，部门负责人→系统管理员两级串行审批', 'PERMISSION', 1, 1, 'admin', '2026-07-01 10:00:00', '2026-07-01 10:00:00'),
    (2, 'RESOURCE_APPROVAL',  '资源访问申请',     '申请访问子系统/API资源，资源Owner→安全审计两级串行审批',   'RESOURCE',   1, 1, 'admin', '2026-07-01 10:00:00', '2026-07-01 10:00:00'),
    (3, 'ROLE_APPROVAL',      '角色变更审批',     '角色变更需判断是否涉及管理员角色(条件分支)，部门经理→总监', 'ROLE',       1, 1, 'admin', '2026-07-05 09:00:00', '2026-07-05 09:00:00'),
    (4, 'COUNTERSIGN_APPROVAL','会签审批流程',     '需要多人同时审批通过才能继续(会签模式)',                   'PERMISSION', 1, 1, 'admin', '2026-07-06 14:00:00', '2026-07-06 14:00:00'),
    (5, 'PARALLEL_APPROVAL',  '并行审批流程',     '财务审批与法务审批同时并行进行，双方都通过后流转',          'RESOURCE',   1, 1, 'admin', '2026-07-08 10:00:00', '2026-07-08 10:00:00'),
    (6, 'SIMPLE_APPROVAL',    '简单审批流程',     '请假/日常申请，直属上级单级审批即可',                      'PERMISSION', 1, 1, 'admin', '2026-07-10 08:00:00', '2026-07-10 08:00:00');

    -- ============================================================
    -- Part 2: 审批节点
    -- ============================================================

    -- 【定义1】权限申请审批: START → 部门负责人审批 → 系统管理员审批 → END
    INSERT IGNORE INTO wf_node (id, definition_id, node_name, node_type, exec_mode, condition_expression, on_condition_fail, approver_strategy, approvers, approver_role, sort_order, timeout_hours, countersign, reject_strategy, create_time) VALUES
    (1,  1, '发起申请',      'START',    'SERIAL',  NULL,               NULL,     'SPECIFIC', NULL,               NULL,      0, 0,  0, NULL,        '2026-07-01 10:00:00'),
    (2,  1, '部门负责人审批', 'APPROVAL', 'SERIAL',  NULL,               NULL,     'ROLE',     NULL,               'dept_leader', 1, 24, 0, 'TO_START', '2026-07-01 10:00:00'),
    (3,  1, '系统管理员审批', 'APPROVAL', 'SERIAL',  NULL,               NULL,     'SPECIFIC', 'admin',            NULL,      2, 48, 0, 'TO_PREV',  '2026-07-01 10:00:00'),
    (4,  1, '审批完成',      'END',      'SERIAL',  NULL,               NULL,     'SPECIFIC', NULL,               NULL,      3, 0,  0, NULL,        '2026-07-01 10:00:00');

    -- 【定义2】资源访问申请: START → 资源Owner审批 → 安全审计 → END
    INSERT IGNORE INTO wf_node (id, definition_id, node_name, node_type, exec_mode, condition_expression, on_condition_fail, approver_strategy, approvers, approver_role, sort_order, timeout_hours, countersign, reject_strategy, create_time) VALUES
    (5,  2, '发起申请',      'START',    'SERIAL',  NULL,               NULL,     'SPECIFIC', NULL,               NULL,      0, 0,  0, NULL,        '2026-07-01 10:00:00'),
    (6,  2, '资源Owner审批', 'APPROVAL', 'SERIAL',  NULL,               NULL,     'SPECIFIC', 'admin',            NULL,      1, 24, 0, 'TO_START', '2026-07-01 10:00:00'),
    (7,  2, '安全审计',      'APPROVAL', 'SERIAL',  NULL,               NULL,     'SPECIFIC', 'wangwu',           NULL,      2, 48, 0, 'TO_PREV',  '2026-07-01 10:00:00'),
    (8,  2, '审批完成',      'END',      'SERIAL',  NULL,               NULL,     'SPECIFIC', NULL,               NULL,      3, 0,  0, NULL,        '2026-07-01 10:00:00');

    -- 【定义3】角色变更审批(条件分支): START → CONDITION → 部门经理 (→ 总监[含admin时]) → END
    INSERT IGNORE INTO wf_node (id, definition_id, node_name, node_type, exec_mode, condition_expression, on_condition_fail, approver_strategy, approvers, approver_role, sort_order, timeout_hours, countersign, reject_strategy, create_time) VALUES
    (9,  3, '发起申请',      'START',    'SERIAL',  NULL,                                 NULL,     'SPECIFIC', NULL,               NULL,         0, 0,  0, NULL,       '2026-07-05 09:00:00'),
    (10, 3, '角色风险检查',   'CONDITION','SERIAL',  '#applyContent.contains(''admin'')', 'SKIP',    'SPECIFIC', NULL,               NULL,         1, 0,  0, NULL,       '2026-07-05 09:00:00'),
    (11, 3, '部门经理审批',   'APPROVAL', 'SERIAL',  NULL,                                NULL,     'SPECIFIC', 'lisi',             NULL,         2, 24, 0, 'TO_START', '2026-07-05 09:00:00'),
    (12, 3, '总监审批',      'APPROVAL', 'SERIAL',  NULL,                                NULL,     'SPECIFIC', 'wangwu',           NULL,         3, 48, 0, 'TO_PREV',  '2026-07-05 09:00:00'),
    (13, 3, '审批完成',      'END',      'SERIAL',  NULL,                                NULL,     'SPECIFIC', NULL,               NULL,         4, 0,  0, NULL,       '2026-07-05 09:00:00');

    -- 【定义4】会签审批: START → 联合审批(会签) → END
    INSERT IGNORE INTO wf_node (id, definition_id, node_name, node_type, exec_mode, condition_expression, on_condition_fail, approver_strategy, approvers, approver_role, sort_order, timeout_hours, countersign, reject_strategy, create_time) VALUES
    (14, 4, '发起申请',      'START',    'SERIAL',  NULL, NULL, 'SPECIFIC', NULL,               NULL, 0, 0,  0, NULL,       '2026-07-06 14:00:00'),
    (15, 4, '联合审批(会签)', 'APPROVAL', 'SERIAL',  NULL, NULL, 'SPECIFIC', 'lisi,wangwu',     NULL, 1, 24, 1, 'TO_START', '2026-07-06 14:00:00'),
    (16, 4, '审批完成',      'END',      'SERIAL',  NULL, NULL, 'SPECIFIC', NULL,               NULL, 2, 0,  0, NULL,       '2026-07-06 14:00:00');

    -- 【定义5】并行审批: START → PARALLEL_START → [财务审批 | 法务审批] → PARALLEL_END → END
    INSERT IGNORE INTO wf_node (id, definition_id, node_name, node_type, exec_mode, condition_expression, on_condition_fail, approver_strategy, approvers, approver_role, sort_order, timeout_hours, countersign, reject_strategy, create_time) VALUES
    (17, 5, '发起申请',      'START',          'SERIAL',  NULL, NULL, 'SPECIFIC', NULL,         NULL, 0, 0,  0, NULL,       '2026-07-08 10:00:00'),
    (18, 5, '并行分支',      'PARALLEL_START',  'SERIAL',  NULL, NULL, 'SPECIFIC', NULL,         NULL, 1, 0,  0, NULL,       '2026-07-08 10:00:00'),
    (19, 5, '财务审批',      'APPROVAL',        'PARALLEL',NULL, NULL, 'SPECIFIC', 'lisi',       NULL, 2, 24, 0, 'TO_START', '2026-07-08 10:00:00'),
    (20, 5, '法务审批',      'APPROVAL',        'PARALLEL',NULL, NULL, 'SPECIFIC', 'wangwu',     NULL, 3, 24, 0, 'TO_START', '2026-07-08 10:00:00'),
    (21, 5, '并行汇合',      'PARALLEL_END',    'SERIAL',  NULL, NULL, 'SPECIFIC', NULL,         NULL, 4, 0,  0, NULL,       '2026-07-08 10:00:00'),
    (22, 5, '审批完成',      'END',             'SERIAL',  NULL, NULL, 'SPECIFIC', NULL,         NULL, 5, 0,  0, NULL,       '2026-07-08 10:00:00');

    -- 【定义6】简单审批: START → 直属上级审批 → END
    INSERT IGNORE INTO wf_node (id, definition_id, node_name, node_type, exec_mode, condition_expression, on_condition_fail, approver_strategy, approvers, approver_role, sort_order, timeout_hours, countersign, reject_strategy, create_time) VALUES
    (23, 6, '发起申请',      'START',    'SERIAL', NULL, NULL, 'SPECIFIC', NULL,  NULL, 0, 0,  0, NULL,       '2026-07-10 08:00:00'),
    (24, 6, '直属上级审批',   'APPROVAL', 'SERIAL', NULL, NULL, 'SPECIFIC', 'lisi',NULL, 1, 24, 0, 'TO_START', '2026-07-10 08:00:00'),
    (25, 6, '审批完成',      'END',      'SERIAL', NULL, NULL, 'SPECIFIC', NULL,  NULL, 2, 0,  0, NULL,       '2026-07-10 08:00:00');

    -- 补全【定义5】并行子节点的 parent_node_id
    UPDATE wf_node SET parent_node_id = 18, parallel_group = 'finance' WHERE id = 19;
    UPDATE wf_node SET parent_node_id = 18, parallel_group = 'legal'   WHERE id = 20;

    -- ============================================================
    -- Part 3: 审批实例 + 审批任务 (覆盖 PENDING/APPROVED/REJECTED/WITHDRAWN)
    -- ============================================================

    -- ------------------------------------------------------------------
    -- 3.1 待审批 PENDING (用于测试审批操作)
    -- ------------------------------------------------------------------

    -- 实例1001: zhangsan申请权限 → lisi待审批 (定义1-单级进行中)
    INSERT IGNORE INTO wf_instance (id, definition_id, title, applicant, apply_content, status, current_node_id, current_approver, approval_chain, approval_records, created_by, create_time, update_time) VALUES
    (1001, 6, '请假申请-年假3天', 'zhangsan',
     '{"leaveType":"年假","startDate":"2026-07-15","endDate":"2026-07-17","days":3,"reason":"家庭出游"}',
     'PENDING', 24, 'lisi', '24', '[]', 'zhangsan', '2026-07-12 09:00:00', '2026-07-12 09:00:00');
    INSERT IGNORE INTO wf_task (id, instance_id, node_id, node_name, approver, status, create_time, update_time) VALUES
    (1001, 1001, 24, '直属上级审批', 'lisi', 'PENDING', '2026-07-12 09:00:00', '2026-07-12 09:00:00');

    -- 实例1002: zhangsan申请权限 → lisi待审批 (定义1-第一级)
    INSERT IGNORE INTO wf_instance (id, definition_id, title, applicant, apply_content, status, current_node_id, current_approver, approval_chain, approval_records, created_by, create_time, update_time) VALUES
    (1002, 1, '申请数据导出权限', 'zhangsan',
     '{"reason":"运营分析需要导出用户行为数据","targetRoles":["data_export"],"duration":"30天"}',
     'PENDING', 2, 'lisi', '2,3', '[]', 'zhangsan', '2026-07-12 10:00:00', '2026-07-12 10:00:00');
    INSERT IGNORE INTO wf_task (id, instance_id, node_id, node_name, approver, status, create_time, update_time) VALUES
    (1002, 1002, 2, '部门负责人审批', 'lisi', 'PENDING', '2026-07-12 10:00:00', '2026-07-12 10:00:00');

    -- 实例1003: zhangsan申请资源 → admin待审批 (定义2-第一级)
    INSERT IGNORE INTO wf_instance (id, definition_id, title, applicant, apply_content, status, current_node_id, current_approver, approval_chain, approval_records, created_by, create_time, update_time) VALUES
    (1003, 2, '申请调用支付API', 'zhangsan',
     '{"reason":"新功能集成支付","resourceType":"API","targetApi":"/api/payment/*","env":"prod"}',
     'PENDING', 6, 'admin', '6,7', '[]', 'zhangsan', '2026-07-12 11:00:00', '2026-07-12 11:00:00');
    INSERT IGNORE INTO wf_task (id, instance_id, node_id, node_name, approver, status, create_time, update_time) VALUES
    (1003, 1003, 6, '资源Owner审批', 'admin', 'PENDING', '2026-07-12 11:00:00', '2026-07-12 11:00:00');

    -- 实例1004: admin申请角色 → lisi待审批 (定义3-角色变更，条件分支)
    INSERT IGNORE INTO wf_instance (id, definition_id, title, applicant, apply_content, status, current_node_id, current_approver, approval_chain, approval_records, created_by, create_time, update_time) VALUES
    (1004, 3, '申请新增管理权限', 'admin',
     '{"reason":"需要管理系统配置","targetRoles":["system_admin"],"containAdmin":true}',
     'PENDING', 11, 'lisi', '11,12', '[]', 'admin', '2026-07-12 13:00:00', '2026-07-12 13:00:00');
    INSERT IGNORE INTO wf_task (id, instance_id, node_id, node_name, approver, status, create_time, update_time) VALUES
    (1004, 1004, 11, '部门经理审批', 'lisi', 'PENDING', '2026-07-12 13:00:00', '2026-07-12 13:00:00');

    -- 实例1005: zhangsan申请会签 → lisi+wangwu待会签 (定义4-会签)
    INSERT IGNORE INTO wf_instance (id, definition_id, title, applicant, apply_content, status, current_node_id, current_approver, approval_chain, approval_records, created_by, create_time, update_time) VALUES
    (1005, 4, '申请生产环境部署权限', 'zhangsan',
     '{"reason":"紧急修复需要直接部署","env":"prod","serviceName":"user-service"}',
     'PENDING', 15, 'lisi,wangwu', '15', '[]', 'zhangsan', '2026-07-12 14:00:00', '2026-07-12 14:00:00');
    INSERT IGNORE INTO wf_task (id, instance_id, node_id, node_name, approver, status, create_time, update_time) VALUES
    (1005, 1005, 15, '联合审批(会签)', 'lisi',   'PENDING', '2026-07-12 14:00:00', '2026-07-12 14:00:00'),
    (1006, 1005, 15, '联合审批(会签)', 'wangwu', 'PENDING', '2026-07-12 14:00:00', '2026-07-12 14:00:00');

    -- 实例1006: zhangsan申请并行审批 → 财务已过法务待批 (定义5-并行)
    INSERT IGNORE INTO wf_instance (id, definition_id, title, applicant, apply_content, status, current_node_id, current_approver, approval_chain, approval_records, created_by, create_time, update_time) VALUES
    (1006, 5, '申请采购服务器', 'zhangsan',
     '{"reason":"新项目上线需扩容","items":[{"name":"云服务器","count":5,"spec":"8C16G"}],"budget":50000}',
     'PENDING', 20, 'wangwu', '19,20,21', '[{"time":"2026-07-12T15:00:00","action":"APPROVE","operator":"lisi","comment":"预算合理，同意采购"}]', 'zhangsan', '2026-07-12 15:00:00', '2026-07-12 16:00:00');
    INSERT IGNORE INTO wf_task (id, instance_id, node_id, node_name, approver, status, comment, approve_time, create_time, update_time) VALUES
    (1007, 1006, 19, '财务审批', 'lisi',   'APPROVED', '预算合理，同意采购', '2026-07-12 16:00:00', '2026-07-12 15:00:00', '2026-07-12 16:00:00'),
    (1008, 1006, 20, '法务审批', 'wangwu', 'PENDING',  NULL,                 NULL,                   '2026-07-12 15:00:00', '2026-07-12 15:00:00');

    -- 实例1007: zhangsan申请权限 → lisi已过admin待批 (定义1-第二级进行中)
    INSERT IGNORE INTO wf_instance (id, definition_id, title, applicant, apply_content, status, current_node_id, current_approver, approval_chain, approval_records, created_by, create_time, update_time) VALUES
    (1007, 1, '申请用户管理权限', 'zhangsan',
     '{"reason":"协助HR管理员工账号","targetRoles":["user_manager"]}',
     'PENDING', 3, 'admin', '2,3', '[{"time":"2026-07-11T10:00:00","action":"APPROVE","operator":"lisi","comment":"同意，转系统管理员审批"}]', 'zhangsan', '2026-07-11 09:00:00', '2026-07-11 10:00:00');
    INSERT IGNORE INTO wf_task (id, instance_id, node_id, node_name, approver, status, comment, approve_time, create_time, update_time) VALUES
    (1009, 1007, 2, '部门负责人审批', 'lisi',  'APPROVED', '同意，转系统管理员审批', '2026-07-11 10:00:00', '2026-07-11 09:00:00', '2026-07-11 10:00:00'),
    (1010, 1007, 3, '系统管理员审批', 'admin', 'PENDING',  NULL,                    NULL,                   '2026-07-11 10:00:00', '2026-07-11 10:00:00');

    -- ------------------------------------------------------------------
    -- 3.2 已通过 APPROVED (用于查看已完成的流程)
    -- ------------------------------------------------------------------

    -- 实例2001: 简单审批-请假已通过
    INSERT IGNORE INTO wf_instance (id, definition_id, title, applicant, apply_content, status, current_node_id, current_approver, approval_chain, approval_records, created_by, create_time, update_time, finish_time) VALUES
    (2001, 6, '请假申请-病假1天', 'zhangsan',
     '{"leaveType":"病假","startDate":"2026-07-08","endDate":"2026-07-08","days":1,"reason":"感冒发烧"}',
     'APPROVED', NULL, NULL, '24',
     '[{"time":"2026-07-07T14:00:00","action":"APPROVE","operator":"lisi","comment":"好好休息"}]',
     'zhangsan', '2026-07-07 13:00:00', '2026-07-07 14:00:00', '2026-07-07 14:00:00');
    INSERT IGNORE INTO wf_task (id, instance_id, node_id, node_name, approver, status, comment, approve_time, create_time, update_time) VALUES
    (2001, 2001, 24, '直属上级审批', 'lisi', 'APPROVED', '好好休息', '2026-07-07 14:00:00', '2026-07-07 13:00:00', '2026-07-07 14:00:00');

    -- 实例2002: 权限审批-两级全部通过
    INSERT IGNORE INTO wf_instance (id, definition_id, title, applicant, apply_content, status, current_node_id, current_approver, approval_chain, approval_records, created_by, create_time, update_time, finish_time) VALUES
    (2002, 1, '申请报表查看权限', 'zhangsan',
     '{"reason":"部门周报分析需要","targetRoles":["report_viewer"]}',
     'APPROVED', NULL, NULL, '2,3',
     '[{"time":"2026-07-09T09:30:00","action":"APPROVE","operator":"lisi","comment":"工作需要，同意"},{"time":"2026-07-09T16:00:00","action":"APPROVE","operator":"admin","comment":"已授权"}]',
     'zhangsan', '2026-07-09 09:00:00', '2026-07-09 16:00:00', '2026-07-09 16:00:00');
    INSERT IGNORE INTO wf_task (id, instance_id, node_id, node_name, approver, status, comment, approve_time, create_time, update_time) VALUES
    (2002, 2002, 2, '部门负责人审批', 'lisi',  'APPROVED', '工作需要，同意', '2026-07-09 09:30:00', '2026-07-09 09:00:00', '2026-07-09 09:30:00'),
    (2003, 2002, 3, '系统管理员审批', 'admin', 'APPROVED', '已授权',         '2026-07-09 16:00:00', '2026-07-09 09:30:00', '2026-07-09 16:00:00');

    -- 实例2003: 资源审批-两级全部通过
    INSERT IGNORE INTO wf_instance (id, definition_id, title, applicant, apply_content, status, current_node_id, current_approver, approval_chain, approval_records, created_by, create_time, update_time, finish_time) VALUES
    (2003, 2, '申请短信网关API', 'zhangsan',
     '{"reason":"营销活动需要发送通知短信","resourceType":"API","targetApi":"/api/sms/*","qps":100}',
     'APPROVED', NULL, NULL, '6,7',
     '[{"time":"2026-07-10T10:00:00","action":"APPROVE","operator":"admin","comment":"已确认资源可用"},{"time":"2026-07-10T15:00:00","action":"APPROVE","operator":"wangwu","comment":"安全审计通过"}]',
     'zhangsan', '2026-07-10 09:00:00', '2026-07-10 15:00:00', '2026-07-10 15:00:00');
    INSERT IGNORE INTO wf_task (id, instance_id, node_id, node_name, approver, status, comment, approve_time, create_time, update_time) VALUES
    (2004, 2003, 6, '资源Owner审批', 'admin',  'APPROVED', '已确认资源可用',   '2026-07-10 10:00:00', '2026-07-10 09:00:00', '2026-07-10 10:00:00'),
    (2005, 2003, 7, '安全审计',      'wangwu', 'APPROVED', '安全审计通过',     '2026-07-10 15:00:00', '2026-07-10 10:00:00', '2026-07-10 15:00:00');

    -- 实例2004: 角色变更-普通角色(没触发总监分支)，仅经理审批通过
    INSERT IGNORE INTO wf_instance (id, definition_id, title, applicant, apply_content, status, current_node_id, current_approver, approval_chain, approval_records, created_by, create_time, update_time, finish_time) VALUES
    (2004, 3, '申请普通用户角色', 'zhangsan',
     '{"reason":"新人入职需要开通基础权限","targetRoles":["普通用户"]}',
     'APPROVED', NULL, NULL, '11',
     '[{"time":"2026-07-11T09:00:00","action":"APPROVE","operator":"lisi","comment":"新人入职，批准"}]',
     'zhangsan', '2026-07-11 08:30:00', '2026-07-11 09:00:00', '2026-07-11 09:00:00');
    INSERT IGNORE INTO wf_task (id, instance_id, node_id, node_name, approver, status, comment, approve_time, create_time, update_time) VALUES
    (2006, 2004, 11, '部门经理审批', 'lisi', 'APPROVED', '新人入职，批准', '2026-07-11 09:00:00', '2026-07-11 08:30:00', '2026-07-11 09:00:00');

    -- 实例2005: 会签审批-全部通过
    INSERT IGNORE INTO wf_instance (id, definition_id, title, applicant, apply_content, status, current_node_id, current_approver, approval_chain, approval_records, created_by, create_time, update_time, finish_time) VALUES
    (2005, 4, '申请数据库直接操作权限', 'admin',
     '{"reason":"紧急数据修复需要DBA权限","targetDb":"auth_platform","tables":["sys_user","sys_role"]}',
     'APPROVED', NULL, NULL, '15',
     '[{"time":"2026-07-08T10:00:00","action":"APPROVE","operator":"lisi","comment":"紧急情况同意"},{"time":"2026-07-08T11:00:00","action":"APPROVE","operator":"wangwu","comment":"已确认，操作后需补充变更记录"}]',
     'admin', '2026-07-08 09:00:00', '2026-07-08 11:00:00', '2026-07-08 11:00:00');
    INSERT IGNORE INTO wf_task (id, instance_id, node_id, node_name, approver, status, comment, approve_time, create_time, update_time) VALUES
    (2007, 2005, 15, '联合审批(会签)', 'lisi',   'APPROVED', '紧急情况同意',                     '2026-07-08 10:00:00', '2026-07-08 09:00:00', '2026-07-08 10:00:00'),
    (2008, 2005, 15, '联合审批(会签)', 'wangwu', 'APPROVED', '已确认，操作后需补充变更记录',       '2026-07-08 11:00:00', '2026-07-08 09:00:00', '2026-07-08 11:00:00');

    -- ------------------------------------------------------------------
    -- 3.3 已驳回 REJECTED (用于查看驳回记录)
    -- ------------------------------------------------------------------

    -- 实例3001: 简单审批-请假被驳回
    INSERT IGNORE INTO wf_instance (id, definition_id, title, applicant, apply_content, status, current_node_id, current_approver, approval_chain, approval_records, created_by, create_time, update_time, finish_time) VALUES
    (3001, 6, '请假申请-事假5天', 'zhangsan',
     '{"leaveType":"事假","startDate":"2026-07-20","endDate":"2026-07-24","days":5,"reason":"个人私事"}',
     'REJECTED', NULL, NULL, '24',
     '[{"time":"2026-07-11T09:00:00","action":"REJECT","operator":"lisi","comment":"项目关键期，暂不能请长假"}]',
     'zhangsan', '2026-07-11 08:00:00', '2026-07-11 09:00:00', '2026-07-11 09:00:00');
    INSERT IGNORE INTO wf_task (id, instance_id, node_id, node_name, approver, status, comment, approve_time, create_time, update_time) VALUES
    (3001, 3001, 24, '直属上级审批', 'lisi', 'REJECTED', '项目关键期，暂不能请长假', '2026-07-11 09:00:00', '2026-07-11 08:00:00', '2026-07-11 09:00:00');

    -- 实例3002: 权限审批-部门经理通过但系统管理员驳回
    INSERT IGNORE INTO wf_instance (id, definition_id, title, applicant, apply_content, status, current_node_id, current_approver, approval_chain, approval_records, created_by, create_time, update_time, finish_time) VALUES
    (3002, 1, '申请数据库删除权限', 'zhangsan',
     '{"reason":"清理测试数据需要DELETE权限","targetRoles":["db_delete"]}',
     'REJECTED', NULL, NULL, '2,3',
     '[{"time":"2026-07-09T10:00:00","action":"APPROVE","operator":"lisi","comment":"测试清理是合理的"},{"time":"2026-07-09T15:00:00","action":"REJECT","operator":"admin","comment":"DELETE权限风险过高，建议使用软删除"}]',
     'zhangsan', '2026-07-09 09:00:00', '2026-07-09 15:00:00', '2026-07-09 15:00:00');
    INSERT IGNORE INTO wf_task (id, instance_id, node_id, node_name, approver, status, comment, approve_time, create_time, update_time) VALUES
    (3002, 3002, 2, '部门负责人审批', 'lisi',  'APPROVED', '测试清理是合理的',                           '2026-07-09 10:00:00', '2026-07-09 09:00:00', '2026-07-09 10:00:00'),
    (3003, 3002, 3, '系统管理员审批', 'admin', 'REJECTED', 'DELETE权限风险过高，建议使用软删除', '2026-07-09 15:00:00', '2026-07-09 10:00:00', '2026-07-09 15:00:00');

    -- 实例3003: 资源审批-资源Owner直接驳回
    INSERT IGNORE INTO wf_instance (id, definition_id, title, applicant, apply_content, status, current_node_id, current_approver, approval_chain, approval_records, created_by, create_time, update_time, finish_time) VALUES
    (3003, 2, '申请用户数据全量导出', 'zhangsan',
     '{"reason":"需要做全量数据分析","resourceType":"API","targetApi":"/api/user/exportAll"}',
     'REJECTED', NULL, NULL, '6',
     '[{"time":"2026-07-08T14:00:00","action":"REJECT","operator":"admin","comment":"涉及用户隐私，需走合规流程"}]',
     'zhangsan', '2026-07-08 13:00:00', '2026-07-08 14:00:00', '2026-07-08 14:00:00');
    INSERT IGNORE INTO wf_task (id, instance_id, node_id, node_name, approver, status, comment, approve_time, create_time, update_time) VALUES
    (3004, 3003, 6, '资源Owner审批', 'admin', 'REJECTED', '涉及用户隐私，需走合规流程', '2026-07-08 14:00:00', '2026-07-08 13:00:00', '2026-07-08 14:00:00');

    -- ------------------------------------------------------------------
    -- 3.4 已撤回 WITHDRAWN (用户自行撤回)
    -- ------------------------------------------------------------------

    -- 实例4001: 用户自行撤回
    INSERT IGNORE INTO wf_instance (id, definition_id, title, applicant, apply_content, status, current_node_id, current_approver, approval_chain, approval_records, created_by, create_time, update_time, finish_time) VALUES
    (4001, 6, '请假申请-年假7天(已撤回)', 'zhangsan',
     '{"leaveType":"年假","startDate":"2026-08-01","endDate":"2026-08-07","days":7,"reason":"计划长途旅行"}',
     'WITHDRAWN', NULL, NULL, '24',
     '[{"time":"2026-07-10T16:00:00","action":"WITHDRAW","operator":"zhangsan","comment":"行程取消，撤回申请"}]',
     'zhangsan', '2026-07-10 15:00:00', '2026-07-10 16:00:00', '2026-07-10 16:00:00');
    INSERT IGNORE INTO wf_task (id, instance_id, node_id, node_name, approver, status, create_time, update_time) VALUES
    (4001, 4001, 24, '直属上级审批', 'lisi', 'PENDING', '2026-07-10 15:00:00', '2026-07-10 16:00:00');

    -- 实例4002: 管理员撤回自己的申请
    INSERT IGNORE INTO wf_instance (id, definition_id, title, applicant, apply_content, status, current_node_id, current_approver, approval_chain, approval_records, created_by, create_time, update_time, finish_time) VALUES
    (4002, 1, '申请超级管理员权限(已撤回)', 'admin',
     '{"reason":"测试临时需要全部权限","targetRoles":["super_admin"]}',
     'WITHDRAWN', NULL, NULL, '2',
     '[{"time":"2026-07-06T11:00:00","action":"WITHDRAW","operator":"admin","comment":"问题已解决，不再需要"}]',
     'admin', '2026-07-06 10:00:00', '2026-07-06 11:00:00', '2026-07-06 11:00:00');
    INSERT IGNORE INTO wf_task (id, instance_id, node_id, node_name, approver, status, create_time, update_time) VALUES
    (4002, 4002, 2, '部门负责人审批', 'lisi', 'PENDING', '2026-07-06 10:00:00', '2026-07-06 11:00:00');

    -- ============================================================
    -- Part 4: 不绑定实例的额外待审批任务 (用于测试审批人待办列表)
    -- ============================================================

    -- 让 lisi 多几个待办，方便测试待审批列表
    INSERT IGNORE INTO wf_instance (id, definition_id, title, applicant, apply_content, status, current_node_id, current_approver, approval_chain, approval_records, created_by, create_time, update_time) VALUES
    (5001, 6, '调休申请', 'zhangsan',
     '{"leaveType":"调休","startDate":"2026-07-18","endDate":"2026-07-18","days":1,"reason":"周末加班调休"}',
     'PENDING', 24, 'lisi', '24', '[]', 'zhangsan', '2026-07-12 16:00:00', '2026-07-12 16:00:00');
    INSERT IGNORE INTO wf_task (id, instance_id, node_id, node_name, approver, status, create_time, update_time) VALUES
    (5001, 5001, 24, '直属上级审批', 'lisi', 'PENDING', '2026-07-12 16:00:00', '2026-07-12 16:00:00');

    INSERT IGNORE INTO wf_instance (id, definition_id, title, applicant, apply_content, status, current_node_id, current_approver, approval_chain, approval_records, created_by, create_time, update_time) VALUES
    (5002, 6, '出差申请-北京', 'zhangsan',
     '{"leaveType":"出差","startDate":"2026-07-22","endDate":"2026-07-24","days":3,"destination":"北京","reason":"客户现场支持"}',
     'PENDING', 24, 'lisi', '24', '[]', 'zhangsan', '2026-07-12 17:00:00', '2026-07-12 17:00:00');
    INSERT IGNORE INTO wf_task (id, instance_id, node_id, node_name, approver, status, create_time, update_time) VALUES
    (5002, 5002, 24, '直属上级审批', 'lisi', 'PENDING', '2026-07-12 17:00:00', '2026-07-12 17:00:00');

    INSERT IGNORE INTO wf_instance (id, definition_id, title, applicant, apply_content, status, current_node_id, current_approver, approval_chain, approval_records, created_by, create_time, update_time) VALUES
    (5003, 6, '申请加班餐费报销', 'zhangsan',
     '{"expenseType":"餐费","amount":150,"reason":"项目上线加班","attachments":["receipt_001.jpg"]}',
     'PENDING', 24, 'lisi', '24', '[]', 'zhangsan', '2026-07-12 18:00:00', '2026-07-12 18:00:00');
    INSERT IGNORE INTO wf_task (id, instance_id, node_id, node_name, approver, status, create_time, update_time) VALUES
    (5003, 5003, 24, '直属上级审批', 'lisi', 'PENDING', '2026-07-12 18:00:00', '2026-07-12 18:00:00');

    -- ============================================================
    -- 验证: 查看数据概览
    -- ============================================================
    SELECT '--- 流程定义 ---' AS info;
    SELECT id, definition_key, definition_name, category, status FROM wf_definition ORDER BY id;

    SELECT '--- 审批节点 ---' AS info;
    SELECT id, definition_id, node_name, node_type, exec_mode, countersign, sort_order FROM wf_node ORDER BY definition_id, sort_order;

    SELECT '--- 流程实例 (按状态) ---' AS info;
    SELECT status, COUNT(*) AS cnt FROM wf_instance GROUP BY status ORDER BY status;

    SELECT '--- 审批任务 (按状态) ---' AS info;
    SELECT status, COUNT(*) AS cnt FROM wf_task GROUP BY status ORDER BY status;

    SELECT '--- 各审批人待办数 ---' AS info;
    SELECT approver, COUNT(*) AS pending_cnt FROM wf_task WHERE status = 'PENDING' GROUP BY approver;
