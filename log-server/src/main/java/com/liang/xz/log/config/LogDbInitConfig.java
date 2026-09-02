package com.liang.xz.log.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 日志数据库表初始化
 *
 * @author liang
 */
@Slf4j
@Configuration
public class LogDbInitConfig {

    @Bean
    CommandLineRunner initLogTables(JdbcTemplate jdbcTemplate) {
        return args -> {
            log.info("[log-server] 检查日志数据库表结构...");

            // ──── 日志记录主表 ────
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_log_record (
                    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
                    trace_id         VARCHAR(64)   COMMENT '链路追踪ID',
                    module           VARCHAR(32)   NOT NULL COMMENT '来源模块',
                    category         VARCHAR(32)   NOT NULL COMMENT '日志分类',
                    level            VARCHAR(16)   NOT NULL COMMENT '日志级别',
                    class_name       VARCHAR(255)  COMMENT '类名',
                    method_name      VARCHAR(128)  COMMENT '方法名',
                    message          TEXT          COMMENT '日志摘要',
                    full_message     MEDIUMTEXT    COMMENT '完整消息',
                    exception_stack  MEDIUMTEXT    COMMENT '异常堆栈',
                    exception_type   VARCHAR(255)  COMMENT '异常类型',
                    error_fingerprint VARCHAR(64)  COMMENT '错误指纹(SHA256)',
                    username         VARCHAR(64)   COMMENT '操作人',
                    client_ip        VARCHAR(64)   COMMENT '客户端IP',
                    request_uri      VARCHAR(512)  COMMENT '请求URI',
                    http_method      VARCHAR(16)   COMMENT 'HTTP方法',
                    http_status      INT           COMMENT 'HTTP状态码',
                    cost_time        BIGINT        COMMENT '耗时(ms)',
                    metadata         JSON          COMMENT '扩展元数据',
                    log_time         DATETIME(3)   NOT NULL COMMENT '日志产生时间',
                    create_time      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                    INDEX idx_trace_id (trace_id),
                    INDEX idx_module (module),
                    INDEX idx_category (category),
                    INDEX idx_level (level),
                    INDEX idx_error_fingerprint (error_fingerprint),
                    INDEX idx_exception_type (exception_type),
                    INDEX idx_log_time (log_time),
                    INDEX idx_module_level_time (module, level, log_time),
                    INDEX idx_category_level_time (category, level, log_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一日志记录表';
                """);

            // ──── 错误解决方案知识库 ────
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_error_solution (
                    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
                    error_fingerprint  VARCHAR(64)   NOT NULL COMMENT '错误指纹',
                    error_pattern      VARCHAR(512)  COMMENT '错误特征描述',
                    root_cause         TEXT          COMMENT '根因分析',
                    solution           TEXT          COMMENT '解决方案',
                    steps              TEXT          COMMENT '详细步骤',
                    reference_url      VARCHAR(512)  COMMENT '参考链接',
                    resolve_count      INT           NOT NULL DEFAULT 0 COMMENT '解决次数',
                    status             VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ARCHIVED',
                    created_by         VARCHAR(64)   COMMENT '创建人',
                    create_time        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    update_time        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                    UNIQUE INDEX uk_fingerprint (error_fingerprint),
                    INDEX idx_status (status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错误解决方案知识库';
                """);

            log.info("[log-server] 日志数据库表结构初始化完成");
        };
    }
}
