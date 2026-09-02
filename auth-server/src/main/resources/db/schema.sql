-- ============================================================
--  授权门户中台 - 数据库初始化脚本
--  基于 Spring Authorization Server 1.2.x 标准表结构
-- ============================================================

-- -----------------------------------------------------------
--  0. 平台用户表 (自定义扩展表)
--     存储授权平台的用户账号信息
--     密码使用 BCrypt 编码存储
--     支持多租户标识
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user
(
    id                      bigint auto_increment primary key                                      COMMENT '用户主键(自增)',
    username                varchar(50)   NOT NULL                                                 COMMENT '用户名(登录账号,唯一)',
    password                varchar(200)  NOT NULL                                                 COMMENT '密码(BCrypt编码)',
    nickname                varchar(100)  DEFAULT NULL                                             COMMENT '用户昵称/显示名称',
    email                   varchar(100)  DEFAULT NULL                                             COMMENT '邮箱',
    phone                   varchar(20)   DEFAULT NULL                                             COMMENT '手机号',
    user_type               varchar(32)   NOT NULL DEFAULT 'user'                                  COMMENT '用户类型: admin=管理员 user=普通用户 service=服务账号',
    tenant_id               varchar(64)   NOT NULL DEFAULT 'default'                               COMMENT '租户ID(多租户隔离)',
    enabled                 tinyint(1)    NOT NULL DEFAULT 1                                       COMMENT '账号是否启用',
    account_non_expired     tinyint(1)    NOT NULL DEFAULT 1                                       COMMENT '账号是否未过期',
    account_non_locked      tinyint(1)    NOT NULL DEFAULT 1                                       COMMENT '账号是否未锁定',
    credentials_non_expired tinyint(1)    NOT NULL DEFAULT 1                                       COMMENT '密码是否未过期',
    create_time             datetime      DEFAULT CURRENT_TIMESTAMP                                COMMENT '创建时间',
    last_login_time         datetime      DEFAULT NULL                                             COMMENT '最后登录时间',
    last_login_ip           varchar(64)   DEFAULT NULL                                             COMMENT '最后登录IP',
    dept_id                 bigint        DEFAULT NULL                                             COMMENT '部门ID',
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4                                                             COMMENT='平台用户表';

-- -----------------------------------------------------------
--  1. 客户端注册表 (Spring Authorization Server 标准表)
--     存储第三方客户端(子系统)的注册信息
--     客户端密钥(client_secret)使用自定义加密算法存储
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS oauth2_registered_client
(
    id                            varchar(100)  NOT NULL                                COMMENT '客户端主键ID(UUID)',
    client_id                     varchar(100)  NOT NULL                                COMMENT '客户端唯一标识(对外clientId)',
    client_id_issued_at           datetime      DEFAULT CURRENT_TIMESTAMP               COMMENT '客户端ID签发时间',
    client_secret                 varchar(200)  DEFAULT NULL                            COMMENT '客户端密钥(加密存储,解密后用BCrypt匹配)',
    client_secret_expires_at      datetime      DEFAULT NULL                            COMMENT '客户端密钥过期时间(NULL表示永不过期)',
    client_name                   varchar(200)  NOT NULL                                COMMENT '客户端名称(子系统名称)',
    client_authentication_methods varchar(1000) NOT NULL                                COMMENT '认证方式(逗号分隔): client_secret_basic/client_secret_post/private_key_jwt/none',
    authorization_grant_types     varchar(1000) NOT NULL                                COMMENT '授权模式(逗号分隔): authorization_code/client_credentials/refresh_token/device_code',
    redirect_uris                 TEXT         DEFAULT NULL                            COMMENT '授权码模式回调地址(JSON数组, 含uri/platform/label)',
    post_logout_redirect_uris     TEXT         DEFAULT NULL                            COMMENT '登出后回调地址(JSON数组, 含uri/platform/label)',
    scopes                        varchar(1000) NOT NULL                                COMMENT '授权范围(逗号分隔): openid/profile/read/write',
    client_settings               varchar(2000) NOT NULL                                COMMENT '客户端配置JSON: require_authorization_consent等',
    token_settings                varchar(2000) NOT NULL                                COMMENT 'Token配置JSON: access_token_ttl/refresh_token_ttl等',
    PRIMARY KEY (id),
    UNIQUE KEY uk_client_id (client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4                                                  COMMENT='客户端注册表(Spring Authorization Server标准表)';

-- -----------------------------------------------------------
--  2. 授权主表 (Spring Authorization Server 标准表)
--     access_token、refresh_token、authorization_code 等全部存在这里
--     记录每一次授权行为的完整生命周期
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS oauth2_authorization
(
    id                            varchar(100) NOT NULL                                COMMENT '授权记录主键(UUID)',
    registered_client_id          varchar(100) NOT NULL                                COMMENT '关联的客户端ID',
    principal_name                varchar(200) NOT NULL                                COMMENT '授权主体名称(用户ID或用户名)',
    authorization_grant_type      varchar(100) NOT NULL                                COMMENT '本次授权模式: authorization_code/client_credentials/refresh_token',
    authorized_scopes             varchar(1000) DEFAULT NULL                           COMMENT '本次实际授权的scope范围',
    attributes                    text          DEFAULT NULL                           COMMENT '扩展属性(JSON)',
    state                         varchar(500)  DEFAULT NULL                           COMMENT '授权码模式的state参数(防CSRF)',
    -- 授权码
    authorization_code_value      text          DEFAULT NULL                           COMMENT '授权码值(加密存储)',
    authorization_code_issued_at  datetime      DEFAULT NULL                           COMMENT '授权码签发时间',
    authorization_code_expires_at datetime      DEFAULT NULL                           COMMENT '授权码过期时间',
    authorization_code_metadata   text          DEFAULT NULL                           COMMENT '授权码元数据(JSON)',
    -- AccessToken
    access_token_value            text          DEFAULT NULL                           COMMENT 'AccessToken值(加密存储)',
    access_token_issued_at        datetime      DEFAULT NULL                           COMMENT 'AccessToken签发时间',
    access_token_expires_at       datetime      DEFAULT NULL                           COMMENT 'AccessToken过期时间',
    access_token_metadata         text          DEFAULT NULL                           COMMENT 'AccessToken元数据(JSON)',
    access_token_type            varchar(100)  DEFAULT NULL                           COMMENT 'AccessToken类型: Bearer/JWT等',
    access_token_scopes          varchar(1000) DEFAULT NULL                           COMMENT 'AccessToken实际授权的scope',
    -- RefreshToken
    refresh_token_value           text          DEFAULT NULL                           COMMENT 'RefreshToken值(加密存储)',
    refresh_token_issued_at       datetime      DEFAULT NULL                           COMMENT 'RefreshToken签发时间',
    refresh_token_expires_at      datetime      DEFAULT NULL                           COMMENT 'RefreshToken过期时间',
    refresh_token_metadata        text          DEFAULT NULL                           COMMENT 'RefreshToken元数据(JSON)',
    -- OIDC ID Token
    oidc_id_token_value           text          DEFAULT NULL                           COMMENT 'OIDC ID Token值',
    oidc_id_token_issued_at       datetime      DEFAULT NULL                           COMMENT 'ID Token签发时间',
    oidc_id_token_expires_at      datetime      DEFAULT NULL                           COMMENT 'ID Token过期时间',
    oidc_id_token_metadata        text          DEFAULT NULL                           COMMENT 'ID Token元数据(JSON)',
    -- DeviceCode (设备授权码模式)
    device_code_value             text          DEFAULT NULL                           COMMENT '设备码值',
    device_code_issued_at         datetime      DEFAULT NULL                           COMMENT '设备码签发时间',
    device_code_expires_at        datetime      DEFAULT NULL                           COMMENT '设备码过期时间',
    device_code_metadata          text          DEFAULT NULL                           COMMENT '设备码元数据(JSON)',
    -- UserCode (设备码对应的用户码)
    user_code_value               text          DEFAULT NULL                           COMMENT '用户码值',
    user_code_issued_at           datetime      DEFAULT NULL                           COMMENT '用户码签发时间',
    user_code_expires_at          datetime      DEFAULT NULL                           COMMENT '用户码过期时间',
    user_code_metadata            text          DEFAULT NULL                           COMMENT '用户码元数据(JSON)',
    PRIMARY KEY (id),
    INDEX idx_access_token (access_token_value(100))                                   COMMENT 'AccessToken查询索引',
    INDEX idx_refresh_token (refresh_token_value(100))                                 COMMENT 'RefreshToken查询索引',
    INDEX idx_principal (principal_name)                                               COMMENT '授权主体查询索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4                                                  COMMENT='授权记录主表(令牌/授权码/设备码统一存储)';

-- -----------------------------------------------------------
--  3. 用户授权同意记录表 (Spring Authorization Server 标准表)
--     记录用户对某个客户端的scope授权同意状态
--     避免每次授权都弹窗确认
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS oauth2_authorization_consent
(
    registered_client_id varchar(100)  NOT NULL                                        COMMENT '客户端ID',
    principal_name       varchar(200)  NOT NULL                                        COMMENT '授权用户ID',
    authorities          varchar(1000) NOT NULL                                        COMMENT '已同意的权限范围(逗号分隔)',
    PRIMARY KEY (registered_client_id, principal_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4                                                  COMMENT='用户授权同意记录表(scope授权免确认)';

-- -----------------------------------------------------------
--  4. 令牌吊销日志表 (自定义扩展表)
--     记录每次Token吊销操作的详细信息，用于安全审计和溯源
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_token_revoke_log
(
    id           bigint auto_increment primary key                                      COMMENT '日志主键(自增)',
    user_id      varchar(64)  not null                                                 COMMENT '被吊销Token的所属用户ID',
    client_id    varchar(64)  not null                                                 COMMENT '被吊销Token的所属客户端ID',
    token_type   varchar(32)  not null                                                 COMMENT '令牌类型: ACCESS_TOKEN / REFRESH_TOKEN',
    token_snip   varchar(128) not null                                                 COMMENT 'Token片段脱敏(前8位+后4位,中间***)',
    revoke_type  tinyint      not null                                                 COMMENT '吊销原因: 1=用户主动登出 2=后台强制下线 3=IAM凭证失效',
    create_time  datetime     default current_timestamp                                COMMENT '吊销时间',
    remark       varchar(255) default ''                                               COMMENT '备注说明'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4                                                  COMMENT='令牌吊销日志表(安全审计追溯)';

-- -----------------------------------------------------------
--  5. 子系统Token记录表 (自定义扩展表)
--     记录子系统通过授权码获取用户信息后自签的Token
--     支持Token刷新(新旧Token关联)和吊销管理
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS subsystem_token
(
    id                        bigint auto_increment primary key                                 COMMENT 'Token记录主键(自增)',
    client_id                 varchar(100)  NOT NULL                                            COMMENT '所属子系统客户端ID',
    user_id                   bigint        DEFAULT NULL                                        COMMENT '用户ID(sys_user.id)',
    username                  varchar(50)   NOT NULL                                            COMMENT '用户名',
    access_token              text          NOT NULL                                            COMMENT '子系统自签的AccessToken(JWT)',
    refresh_token             text          DEFAULT NULL                                        COMMENT '子系统自签的RefreshToken',
    token_type                varchar(32)   NOT NULL DEFAULT 'JWT_BEARER'                        COMMENT 'Token类型: JWT_BEARER/CUSTOM',
    access_token_expires_at   datetime      DEFAULT NULL                                        COMMENT 'AccessToken过期时间',
    refresh_token_expires_at  datetime      DEFAULT NULL                                        COMMENT 'RefreshToken过期时间',
    status                    varchar(20)   NOT NULL DEFAULT 'ACTIVE'                            COMMENT 'Token状态: ACTIVE/EXPIRED/REVOKED/REFRESHED',
    parent_token_id           bigint        DEFAULT NULL                                        COMMENT '刷新来源Token记录ID(当前Token是通过哪个旧Token刷新来的)',
    issued_ip                 varchar(64)   DEFAULT NULL                                        COMMENT '签发IP',
    user_agent                varchar(500)  DEFAULT NULL                                        COMMENT '签发时的User-Agent',
    create_time               datetime      DEFAULT CURRENT_TIMESTAMP                           COMMENT '创建时间',
    last_refresh_time         datetime      DEFAULT NULL                                        COMMENT '最后刷新时间',
    refresh_count             int           DEFAULT 0                                            COMMENT '刷新次数',
    revoke_time               datetime      DEFAULT NULL                                        COMMENT '吊销时间',
    revoke_reason             tinyint       DEFAULT NULL                                        COMMENT '吊销原因: 1=用户登出 2=后台强制下线 3=Token过期 4=密钥轮转',
    remark                    varchar(255)  DEFAULT ''                                           COMMENT '备注',
    INDEX idx_client_user (client_id, username)                                                 COMMENT '客户端+用户查询索引',
    INDEX idx_status (status)                                                                   COMMENT '状态查询索引',
    INDEX idx_refresh_token (refresh_token(100))                                                 COMMENT 'RefreshToken查询索引',
    INDEX idx_access_token (access_token(100))                                                  COMMENT 'AccessToken查询索引',
    INDEX idx_create_time (create_time)                                                          COMMENT '时间排序索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4                                                          COMMENT='子系统Token记录表(子系统自签Token的记录/刷新/吊销管理)';

-- -----------------------------------------------------------
--  6. 请求日志表 (自定义扩展表)
--     记录每一次API请求的完整上下文，用于审计追溯和问题排查
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_request_log
(
    id              bigint auto_increment primary key                                 COMMENT '日志主键(自增)',
    trace_id        varchar(32)   NOT NULL                                            COMMENT '请求追踪ID(串联调用链路)',
    request_uri     varchar(500)  NOT NULL                                            COMMENT '请求URI',
    http_method     varchar(10)   NOT NULL                                            COMMENT 'HTTP方法: GET/POST/PUT/DELETE',
    client_ip       varchar(64)   DEFAULT NULL                                        COMMENT '客户端IP',
    source          varchar(20)   DEFAULT 'DIRECT'                                    COMMENT '请求来源: GATEWAY/DIRECT',
    module          varchar(32)   DEFAULT 'GENERAL'                                   COMMENT '操作模块: AUTH/CLIENT/AUDIT/TOKEN/USER/ENTERPRISE',
    operation       varchar(200)  DEFAULT NULL                                        COMMENT '操作描述(Controller类名.方法名)',
    request_params  text          DEFAULT NULL                                        COMMENT '请求参数(脱敏后JSON, 截断2000字符)',
    response_body   text          DEFAULT NULL                                        COMMENT '响应结果(截断2000字符)',
    http_status     int           NOT NULL DEFAULT 200                                COMMENT 'HTTP状态码',
    cost_time       bigint        NOT NULL DEFAULT 0                                  COMMENT '请求耗时(毫秒)',
    username        varchar(50)   DEFAULT NULL                                        COMMENT '操作用户名(从JWT中提取)',
    tenant_id       varchar(64)   DEFAULT 'default'                                   COMMENT '租户ID',
    user_agent      varchar(500)  DEFAULT NULL                                        COMMENT 'User-Agent',
    success         tinyint(1)    NOT NULL DEFAULT 1                                  COMMENT '是否成功: 1=成功, 0=失败',
    error_msg       varchar(500)  DEFAULT NULL                                        COMMENT '错误信息',
    error_stack     text          DEFAULT NULL                                        COMMENT '错误堆栈(截断500字符)',
    create_time     datetime      DEFAULT CURRENT_TIMESTAMP                           COMMENT '创建时间',
    INDEX idx_trace_id (trace_id)                                                      COMMENT 'traceId查询索引',
    INDEX idx_username (username)                                                      COMMENT '用户名查询索引',
    INDEX idx_request_uri (request_uri(100))                                            COMMENT 'URI查询索引',
    INDEX idx_http_status (http_status)                                                 COMMENT '状态码查询索引',
    INDEX idx_success (success)                                                         COMMENT '成功/失败索引',
    INDEX idx_create_time (create_time)                                                 COMMENT '时间排序索引',
    INDEX idx_module (module)                                                           COMMENT '模块索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4                                                  COMMENT='请求日志表(API请求审计追溯)';

-- -----------------------------------------------------------
--  7. 企业信息表 (自定义扩展表)
--     存储接入授权中台的企业/组织的基本信息
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS enterprise_info
(
    id                bigint auto_increment primary key                                 COMMENT '企业主键(自增)',
    enterprise_code   varchar(64)   NOT NULL                                            COMMENT '企业唯一编码(业务标识)',
    enterprise_name   varchar(200)  NOT NULL                                            COMMENT '企业名称',
    short_name        varchar(100)  DEFAULT NULL                                        COMMENT '企业简称',
    enterprise_type   varchar(32)   NOT NULL DEFAULT 'ENTERPRISE'                       COMMENT '企业类型: ENTERPRISE/GOVERNMENT/SCHOOL/HOSPITAL',
    credit_code       varchar(50)   DEFAULT NULL                                        COMMENT '统一社会信用代码',
    legal_person      varchar(50)   DEFAULT NULL                                        COMMENT '法定代表人',
    contact_name      varchar(50)   DEFAULT NULL                                        COMMENT '联系人姓名',
    contact_phone     varchar(20)   DEFAULT NULL                                        COMMENT '联系人手机号',
    contact_email     varchar(100)  DEFAULT NULL                                        COMMENT '联系人邮箱',
    address           varchar(300)  DEFAULT NULL                                        COMMENT '企业地址',
    status            varchar(20)   NOT NULL DEFAULT 'ACTIVE'                            COMMENT '状态: ACTIVE/DISABLED/PENDING',
    tenant_id         varchar(64)   NOT NULL DEFAULT 'default'                           COMMENT '所属租户ID',
    create_time       datetime      DEFAULT CURRENT_TIMESTAMP                           COMMENT '创建时间',
    update_time       datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_enterprise_code (enterprise_code)                                     COMMENT '企业编码唯一索引',
    INDEX idx_tenant (tenant_id)                                                        COMMENT '租户索引',
    INDEX idx_status (status)                                                           COMMENT '状态索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4                                                  COMMENT='企业信息表';

-- -----------------------------------------------------------
--  8. 企业微信配置表 (自定义扩展表)
--     存储企业与企微应用/企业微信的绑定配置信息
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS enterprise_wechat_config
(
    id                bigint auto_increment primary key                                 COMMENT '主键(自增)',
    enterprise_id     bigint        NOT NULL                                            COMMENT '关联企业ID',
    corp_id           varchar(100)  NOT NULL                                            COMMENT '企业微信CorpId',
    agent_id          varchar(50)   NOT NULL                                            COMMENT '企业微信应用AgentId',
    app_secret        varchar(200)  NOT NULL                                            COMMENT '应用Secret(加密存储)',
    token             varchar(100)  DEFAULT NULL                                        COMMENT '回调验证Token',
    encoding_aes_key  varchar(100)  DEFAULT NULL                                        COMMENT '消息加解密AESKey',
    app_name          varchar(100)  DEFAULT NULL                                        COMMENT '应用名称',
    enabled           tinyint(1)    NOT NULL DEFAULT 1                                  COMMENT '是否启用企微集成',
    callback_url      varchar(300)  DEFAULT NULL                                        COMMENT '回调URL',
    create_time       datetime      DEFAULT CURRENT_TIMESTAMP                           COMMENT '创建时间',
    update_time       datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_enterprise_id (enterprise_id)                                         COMMENT '一个企业只配置一个企微应用',
    INDEX idx_corp_id (corp_id)                                                         COMMENT 'CorpId索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4                                                  COMMENT='企业微信配置表';

-- -----------------------------------------------------------
--  9. 企业用户关联表 (自定义扩展表)
--     记录平台用户与企业的关联关系
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS enterprise_user_rel
(
    id                bigint auto_increment primary key                                 COMMENT '主键(自增)',
    enterprise_id     bigint        NOT NULL                                            COMMENT '企业ID',
    user_id           bigint        NOT NULL                                            COMMENT '平台用户ID',
    wechat_user_id    varchar(100)  DEFAULT NULL                                        COMMENT '企微UserID',
    role              varchar(20)   NOT NULL DEFAULT 'MEMBER'                            COMMENT '角色: ADMIN/MEMBER',
    status            varchar(20)   NOT NULL DEFAULT 'ACTIVE'                            COMMENT '状态: ACTIVE/DISABLED',
    create_time       datetime      DEFAULT CURRENT_TIMESTAMP                           COMMENT '关联时间',
    UNIQUE KEY uk_user_enterprise (user_id, enterprise_id)                              COMMENT '用户+企业唯一索引',
    INDEX idx_enterprise (enterprise_id)                                                COMMENT '企业索引',
    INDEX idx_wechat_user (wechat_user_id)                                              COMMENT '企微UserID索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4                                                  COMMENT='企业用户关联表';

-- -----------------------------------------------------------
-- 10. 企业消息记录表 (自定义扩展表)
--     记录企业相关的消息推送/短信发送/通知等操作日志
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS enterprise_message_log
(
    id                  bigint auto_increment primary key                               COMMENT '主键(自增)',
    enterprise_id       bigint        NOT NULL                                          COMMENT '企业ID',
    message_type        varchar(30)   NOT NULL                                          COMMENT '消息类型: WECHAT_TEXT/WECHAT_MARKDOWN/SMS/EMAIL',
    title               varchar(200)  NOT NULL                                          COMMENT '消息标题',
    content             text          NOT NULL                                          COMMENT '消息内容',
    recipients          varchar(500)  NOT NULL                                          COMMENT '接收人(逗号分隔)',
    status              varchar(20)   NOT NULL DEFAULT 'PENDING'                         COMMENT '状态: PENDING/SUCCESS/FAILED',
    third_party_msg_id  varchar(100)  DEFAULT NULL                                      COMMENT '第三方返回的消息ID',
    error_msg           varchar(500)  DEFAULT NULL                                      COMMENT '错误信息',
    send_time           datetime      DEFAULT NULL                                      COMMENT '发送时间',
    create_time         datetime      DEFAULT CURRENT_TIMESTAMP                         COMMENT '创建时间',
    INDEX idx_enterprise (enterprise_id)                                                COMMENT '企业索引',
    INDEX idx_status (status)                                                           COMMENT '状态索引',
    INDEX idx_create_time (create_time)                                                 COMMENT '时间索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4                                                  COMMENT='企业消息记录表(企微/短信/邮件推送日志)';
