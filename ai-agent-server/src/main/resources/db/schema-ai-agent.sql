-- ============================================================
-- AI智能体服务 数据库表结构
-- ============================================================

-- 知识库表
CREATE TABLE IF NOT EXISTS ai_knowledge_base (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL COMMENT '知识库名称',
    description VARCHAR(1000) COMMENT '知识库描述',
    doc_count   INT           DEFAULT 0 COMMENT '文档数量(冗余)',
    status      VARCHAR(20)   DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/DISABLED',
    created_at  DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_kb_name (name),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI知识库';

-- 知识库文档表
CREATE TABLE IF NOT EXISTS ai_knowledge_doc (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_name     VARCHAR(100)  NOT NULL COMMENT '知识库名称',
    title       VARCHAR(500)  NOT NULL COMMENT '文档标题',
    content     LONGTEXT      NOT NULL COMMENT '文档全文内容',
    content_type VARCHAR(50)  DEFAULT 'TEXT' COMMENT '内容类型: TEXT/HTML/MARKDOWN/PDF/DOCX/XLSX/CODE/JSON/CSV',
    file_name   VARCHAR(500)  COMMENT '原始文件名',
    file_size   BIGINT        DEFAULT 0 COMMENT '原始文件大小(字节)',
    chunk_count INT           DEFAULT 0 COMMENT '分割块数',
    splitter_type VARCHAR(50) DEFAULT 'PARAGRAPH' COMMENT '切片策略: PARAGRAPH/MARKDOWN/HTML/CODE/JSON/CSV/CHARACTER',
    chunk_size  INT           DEFAULT 500 COMMENT '切片大小',
    overlap     INT           DEFAULT 50 COMMENT '切片重叠字符数',
    status      VARCHAR(20)   DEFAULT 'PENDING' COMMENT '状态: PENDING/CHUNKING/EMBEDDING/INDEXED/FAILED',
    created_at  DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_name (kb_name),
    INDEX idx_status  (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI知识库文档';

-- 搜索记录表
CREATE TABLE IF NOT EXISTS ai_search_record (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    query_text   VARCHAR(1000) NOT NULL COMMENT '搜索查询文本',
    search_type  VARCHAR(20)   NOT NULL COMMENT '搜索类型: LOCAL/INTERNET/RAG/HYBRID',
    result_count INT           DEFAULT 0 COMMENT '结果数量',
    results_json LONGTEXT      COMMENT '搜索结果JSON',
    user_id      VARCHAR(100)  COMMENT '用户ID',
    ip_address   VARCHAR(50)   COMMENT '客户端IP',
    latency_ms   BIGINT        COMMENT '搜索耗时(毫秒)',
    created_at   DATETIME      DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_search_type (search_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI搜索记录';

-- 分析预警记录表
CREATE TABLE IF NOT EXISTS ai_analysis_alert (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_name      VARCHAR(200)  NOT NULL COMMENT '预警名称',
    analysis_type   VARCHAR(50)   NOT NULL COMMENT '分析类型: TREND/ANOMALY/THRESHOLD/PREDICTION/INSIGHT',
    data_source     VARCHAR(200)  COMMENT '数据来源表/接口',
    alert_level     VARCHAR(20)   NOT NULL COMMENT '预警级别: INFO/WARN/CRITICAL',
    alert_content   TEXT          COMMENT '预警摘要',
    analysis_detail LONGTEXT      COMMENT '详细分析报告',
    suggestion      TEXT          COMMENT 'AI建议措施',
    metrics_json    LONGTEXT      COMMENT '关键指标JSON',
    is_read         TINYINT       DEFAULT 0 COMMENT '是否已读',
    resolved        TINYINT       DEFAULT 0 COMMENT '是否已处理',
    resolved_at     DATETIME      COMMENT '处理时间',
    resolved_by     VARCHAR(100)  COMMENT '处理人',
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_alert_level (alert_level),
    INDEX idx_analysis_type (analysis_type),
    INDEX idx_resolved (resolved),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI分析预警记录';

-- 知识库文档块表(存储分割后的chunk + 向量)
CREATE TABLE IF NOT EXISTS ai_knowledge_chunk (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id      BIGINT         NOT NULL COMMENT '关联文档ID',
    chunk_index INT            NOT NULL COMMENT '块序号(从0开始)',
    chunk_text  TEXT           NOT NULL COMMENT '文本块内容',
    vector_json LONGTEXT       COMMENT '向量JSON(仅在DB模式存储)',
    token_count INT            DEFAULT 0 COMMENT 'Token估算数',
    created_at  DATETIME       DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_doc_id (doc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI知识库文档块(Chunk)';

-- AI 对话会话表
CREATE TABLE IF NOT EXISTS ai_chat_session (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id  VARCHAR(64)   NOT NULL COMMENT '会话唯一标识',
    user_id     VARCHAR(100)  COMMENT '用户ID',
    title       VARCHAR(200)  COMMENT '会话标题(首条问题摘要)',
    status      VARCHAR(20)   DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/ARCHIVED/DELETED',
    created_at  DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_session_id (session_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话会话';

-- AI 对话消息表(持久化 ChatMemory)
CREATE TABLE IF NOT EXISTS ai_chat_message (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id   VARCHAR(64)   NOT NULL COMMENT '会话ID',
    user_id      VARCHAR(100)  COMMENT '用户ID(发送者, 历史展示用)',
    user_name    VARCHAR(100)  COMMENT '用户名称(发送者昵称/用户名, 冗余存储)',
    role         VARCHAR(20)   NOT NULL COMMENT '角色: system/user/assistant/tool',
    content      LONGTEXT      COMMENT '文本内容',
    attachments_json LONGTEXT  COMMENT '附件元数据JSON(图片URL/文件信息等)',
    tools_used   VARCHAR(500)  COMMENT '本次回答使用的工具名,逗号分隔',
    latency_ms   BIGINT        COMMENT '响应耗时毫秒',
    create_time  DATETIME      DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话消息';

-- 文档元素元数据(记录PDF/Word深度分析出的各类元素: 图片/表格/扫描页/页眉页脚等)
-- 说明: 与 ai_knowledge_chunk 的区别 ——
--   ai_knowledge_chunk 存的是"分块后的文本", 面向向量检索;
--   本表存的是"原始文档的结构化元素", 面向结构还原与可信度追溯。
--   二者通过 doc_id 关联, 可从检索命中的 chunk 反查其来源元素的类型、位置与置信度。
CREATE TABLE IF NOT EXISTS ai_document_element (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id            BIGINT        COMMENT '关联文档ID(ai_knowledge_doc.id)',
    order_index       INT           DEFAULT 0 COMMENT '元素序号(按阅读顺序)',
    element_type      VARCHAR(30)   NOT NULL COMMENT '元素类型: TEXT/HEADING/TABLE/IMAGE/CHART/SCANNED_PAGE/HEADER/FOOTER/FOOTNOTE/ANNOTATION/WATERMARK/UNKNOWN',
    page_no           INT           DEFAULT 0 COMMENT '页码(从1开始, Word无分页信息时为0)',
    bbox_x0           FLOAT         COMMENT '归一化坐标: 左上X(0~1)',
    bbox_y0           FLOAT         COMMENT '归一化坐标: 左上Y(0~1)',
    bbox_x1           FLOAT         COMMENT '归一化坐标: 右下X(0~1)',
    bbox_y1           FLOAT         COMMENT '归一化坐标: 右下Y(0~1)',
    processing_method VARCHAR(30)   NOT NULL COMMENT '处理方式: DIRECT_EXTRACT/VISION_MODEL/TABLE_STRUCTURED/MODEL_ENHANCED/RULE_INFERRED/SKIPPED',
    confidence        DOUBLE        DEFAULT 1.0 COMMENT '置信度(0~1)',
    content           LONGTEXT      COMMENT '元素内容(表格为Markdown; 图片为视觉描述)',
    table_rows        INT           DEFAULT 0 COMMENT '表格行数(非表格元素为0)',
    table_columns     INT           DEFAULT 0 COMMENT '表格列数(非表格元素为0)',
    remark            VARCHAR(500)  COMMENT '备注(图片尺寸/合并单元格/降级原因等)',
    create_time       DATETIME      DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_doc_id (doc_id),
    INDEX idx_element_type (element_type),
    INDEX idx_doc_type (doc_id, element_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档元素元数据(PDF/Word深度分析)';

-- 多Agent编排-子任务调用日志(记录每个子Agent的调用明细, 用于编排链路排障与Agent质量分析)
-- 说明: 与 sys_ai_invoke_log 的区别 ——
--   sys_ai_invoke_log 是系统级 LLM 调用日志, 面向 token 消耗与供应商质量统计;
--   本表是编排级日志, 面向"多Agent协作链路"的可观测性, 记录任务图结构(层级/依赖)与每个Agent的产出。
CREATE TABLE IF NOT EXISTS ai_agent_task_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id       VARCHAR(64)   NOT NULL COMMENT '编排链路追踪ID, 串联一次多Agent协作的全部子任务',
    session_id     VARCHAR(64)   COMMENT '对话会话ID, 便于关联到具体对话',
    task_id        VARCHAR(64)   NOT NULL COMMENT '子任务ID(LLM规划时生成, 如 t1/t2)',
    parent_trace_id VARCHAR(64)  COMMENT '父链路ID, 预留用于嵌套编排',
    agent_id       VARCHAR(50)   NOT NULL COMMENT '执行该子任务的Agent标识(metrics/alert/knowledge/query)',
    task_name      VARCHAR(200)  COMMENT '子任务名称',
    layer_index    INT           DEFAULT 0 COMMENT '执行层级(拓扑排序后的层号, 0为起始层)',
    depends_on     VARCHAR(200)  COMMENT '依赖的上游任务ID, 逗号分隔',
    task_input     TEXT          COMMENT '子任务输入(含注入的上游产出)',
    task_output    TEXT          COMMENT '子任务输出(Agent返回内容)',
    elapsed_ms     BIGINT        DEFAULT 0 COMMENT '执行耗时毫秒',
    success        TINYINT(1)    DEFAULT 1 COMMENT '是否成功: 1成功 0失败',
    error_msg      VARCHAR(1000) COMMENT '失败原因',
    create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_trace_id (trace_id),
    INDEX idx_session_id (session_id),
    INDEX idx_agent_id (agent_id),
    INDEX idx_create_time (create_time),
    INDEX idx_success (success)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多Agent编排-子任务调用日志';

-- 多Agent编排-编排汇总日志(一次完整编排一条, 记录整体规划与执行概况)
CREATE TABLE IF NOT EXISTS ai_agent_workflow_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id        VARCHAR(64)   NOT NULL COMMENT '编排链路追踪ID',
    session_id      VARCHAR(64)   COMMENT '对话会话ID',
    question        VARCHAR(1000) COMMENT '用户原始问题',
    agent_ids       VARCHAR(200)  COMMENT '参与本次编排的Agent标识, 逗号分隔',
    task_count      INT           DEFAULT 0 COMMENT '子任务总数',
    layer_count     INT           DEFAULT 0 COMMENT '执行层级数',
    success_count   INT           DEFAULT 0 COMMENT '成功子任务数',
    fail_count      INT           DEFAULT 0 COMMENT '失败子任务数',
    elapsed_ms      BIGINT        DEFAULT 0 COMMENT '整体耗时毫秒',
    fallback        TINYINT(1)    DEFAULT 0 COMMENT '是否回退到单链路: 1是 0否',
    remark          VARCHAR(500)  COMMENT '备注(如回退原因)',
    create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_trace_id (trace_id),
    INDEX idx_session_id (session_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多Agent编排-编排汇总日志';

-- 文件上传任务表(用于进度持久化,页面刷新后可恢复)
CREATE TABLE IF NOT EXISTS ai_upload_task (
    id          VARCHAR(64)   PRIMARY KEY COMMENT '任务ID',
    doc_id      BIGINT        COMMENT '关联文档ID',
    kb_name     VARCHAR(100)  NOT NULL COMMENT '知识库名称',
    file_name   VARCHAR(500)  COMMENT '原始文件名',
    file_size   BIGINT        DEFAULT 0 COMMENT '文件大小(字节)',
    status      VARCHAR(20)   DEFAULT 'UPLOADING' COMMENT 'UPLOADING/PARSING/CHUNKING/EMBEDDING/INDEXED/FAILED',
    progress    INT           DEFAULT 0 COMMENT '进度百分比0-100',
    error_msg   VARCHAR(1000) COMMENT '失败原因',
    created_at  DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_doc_id (doc_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI知识库文件上传任务';

-- ----------------------------
-- AI 供应商配置表(sys_ai_provider) 与 AI 复杂度路由配置表(sys_ai_complexity_routing)
-- 及其菜单/按钮权限已迁移至 system-server/src/main/resources/db/schema.sql
-- 原因：这两张表属于「配置管理」能力，统一由 system-server 提供维护界面与 CRUD 接口。
-- ----------------------------
