-- ==================================================
-- 消息广播服务 - 建表语句
-- 运行: auth-message 模块启动时自动执行(sql.init.mode=always)
-- ==================================================

-- 1. 消息记录表
CREATE TABLE IF NOT EXISTS msg_record (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    message_id          VARCHAR(64)  NOT NULL COMMENT '消息ID(UUID)',
    message_type        VARCHAR(50)  NOT NULL COMMENT '消息类型: SYSTEM_NOTICE/EVENT_PUSH/APPROVAL_NOTIFY/SUBSYSTEM_COMM/USER_MESSAGE',
    title               VARCHAR(500) NOT NULL COMMENT '消息标题',
    content             TEXT         COMMENT '消息内容',
    channels            VARCHAR(200) COMMENT '发送渠道: SMS/EMAIL/IN_APP/WEBSOCKET/MQ',
    status              VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态: PENDING/SENDING/SENT/PARTIAL/FAILED',
    source_system       VARCHAR(100) COMMENT '来源系统',
    sender              VARCHAR(100) COMMENT '发送者',
    receivers           VARCHAR(2000) COMMENT '接收者(多个逗号分隔)',
    target_subsystems   VARCHAR(2000) COMMENT '目标子系统(JSON数组)',
    business_id         VARCHAR(200) COMMENT '业务关联ID',
    is_read             TINYINT(1) DEFAULT 0 COMMENT '是否已读(站内信)',
    fail_reason         VARCHAR(1000) COMMENT '失败原因',
    retry_count         INT DEFAULT 0 COMMENT '重试次数',
    template_code       VARCHAR(100) COMMENT '模板编码',
    create_time         DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    send_time           DATETIME COMMENT '发送时间',
    read_time           DATETIME COMMENT '阅读时间',
    UNIQUE INDEX uk_message_id (message_id),
    INDEX idx_type (message_type),
    INDEX idx_status (status),
    INDEX idx_business_id (business_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息记录表';

-- 2. 消息模板表
CREATE TABLE IF NOT EXISTS msg_template (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    template_code       VARCHAR(100) NOT NULL COMMENT '模板编码(唯一)',
    template_name       VARCHAR(200) NOT NULL COMMENT '模板名称',
    channel             VARCHAR(50)  NOT NULL COMMENT '渠道: SMS/EMAIL/IN_APP/WECHAT',
    title_template      VARCHAR(500) COMMENT '标题模板(变量: {{var}})',
    content_template    TEXT         NOT NULL COMMENT '内容模板(变量: {{var}})',
    variables           VARCHAR(500) COMMENT '变量说明(JSON)',
    status              TINYINT DEFAULT 1 COMMENT '状态: 0-停用 1-启用',
    created_by          VARCHAR(100) COMMENT '创建人',
    create_time         DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_template_code (template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息模板表';

-- 3. 插入默认消息模板
INSERT IGNORE INTO msg_template(template_code, template_name, channel, title_template, content_template, variables, status, created_by) VALUES
('APPROVAL_NOTIFY',  '审批结果通知', 'IN_APP',
 '审批结果: {{result}}',
 '您好 {{applicant}}, 您提交的【{{title}}】已被 {{approver}} {{result}}。审批意见: {{comment}}',
 '[{"name":"applicant","desc":"申请人"},{"name":"title","desc":"申请标题"},{"name":"result","desc":"审批结果(通过/驳回)"},{"name":"approver","desc":"审批人"},{"name":"comment","desc":"审批意见"}]',
 1, 'system'),

('PERMISSION_GRANT', '权限开通通知', 'IN_APP',
 '权限已开通: {{permission}}',
 '您好 {{username}}, 您的【{{permission}}】权限已开通。生效时间: {{time}}',
 '[{"name":"username","desc":"用户名"},{"name":"permission","desc":"权限名称"},{"name":"time","desc":"生效时间"}]',
 1, 'system'),

('SYSTEM_NOTICE', '系统公告模板', 'IN_APP',
 '系统公告: {{title}}',
 '【{{title}}】{{content}}\n\n发布人: {{publisher}}\n发布时间: {{time}}',
 '[{"name":"title","desc":"公告标题"},{"name":"content","desc":"公告内容"},{"name":"publisher","desc":"发布人"},{"name":"time","desc":"发布时间"}]',
 1, 'system'),

('EVENT_NOTIFY', '事件通知模板', 'WEBSOCKET',
 '事件: {{eventType}}',
 '{"eventType":"{{eventType}}","data":{{data}},"timestamp":"{{time}}"}',
 '[{"name":"eventType","desc":"事件类型"},{"name":"data","desc":"事件数据JSON"}]',
 1, 'system');

-- 4. 子系统异常反馈表(子系统出问题时反馈到门户并收集日志信息)
CREATE TABLE IF NOT EXISTS subsystem_incident (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    subsystem     VARCHAR(100) NOT NULL COMMENT '子系统标识(client_id), 如 subsystem-a',
    subsystem_name VARCHAR(200) COMMENT '子系统名称',
    incident_type VARCHAR(50)  COMMENT '异常类型: ERROR/HEALTH/SECURITY',
    level         VARCHAR(20)  COMMENT '级别: ERROR/WARN/CRITICAL',
    title         VARCHAR(500) NOT NULL COMMENT '异常标题',
    content       TEXT         COMMENT '异常描述',
    stack_trace   TEXT         COMMENT '异常堆栈',
    trace_id      VARCHAR(100) COMMENT '关联日志traceId(可到log-server按traceId查日志)',
    status        VARCHAR(20)  DEFAULT 'PENDING' COMMENT 'PENDING/RESOLVED/IGNORED',
    reported_at   DATETIME     COMMENT '上报时间',
    resolved_at   DATETIME     COMMENT '处理时间',
    resolver      VARCHAR(100) COMMENT '处理人',
    resolve_note  VARCHAR(500) COMMENT '处理说明',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_subsystem (subsystem),
    INDEX idx_status (status),
    INDEX idx_level (level),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='子系统异常反馈表';
