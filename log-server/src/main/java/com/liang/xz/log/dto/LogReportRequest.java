package com.liang.xz.log.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日志上报请求(单条/批量通用)
 *
 * @author liang
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogReportRequest {

    /** 批量日志条目 */
    private List<LogEntry> entries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogEntry {

        /** 链路追踪ID */
        private String traceId;

        /** 来源模块 */
        private String module;

        /** 日志分类 */
        private String category;

        /** 日志级别 */
        private String level;

        /** 类名 */
        private String className;

        /** 方法名 */
        private String methodName;

        /** 日志消息 */
        private String message;

        /** 完整消息 */
        private String fullMessage;

        /** 异常堆栈 */
        private String exceptionStack;

        /** 异常类型 */
        private String exceptionType;

        /** 操作人 */
        private String username;

        /** 客户端IP */
        private String clientIp;

        /** 请求URI */
        private String requestUri;

        /** HTTP方法 */
        private String httpMethod;

        /** HTTP状态码 */
        private Integer httpStatus;

        /** 耗时(毫秒) */
        private Long costTime;

        /** 扩展元数据(JSON) */
        private String metadata;

        /** 日志产生时间 */
        private LocalDateTime logTime;
    }
}
