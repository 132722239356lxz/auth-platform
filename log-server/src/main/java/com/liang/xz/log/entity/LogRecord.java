package com.liang.xz.log.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * <p>统一日志记录实体 —— 映射 sys_log_record 表</p>
 * <p>存储所有模块上报的日志, 是AI分析的核心数据源</p>
 *
 * @author liang
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_log_record")
public class LogRecord {

    @Id
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "链路追踪ID")
    private String traceId;

    @Schema(description = "来源模块")
    private String module;

    @Schema(description = "日志分类")
    private String category;

    @Schema(description = "日志级别: DEBUG/INFO/WARN/ERROR")
    private String level;

    @Schema(description = "类名全路径")
    private String className;

    @Schema(description = "方法名")
    private String methodName;

    @Schema(description = "日志摘要")
    private String message;

    @Schema(description = "完整消息/请求体/响应体")
    private String fullMessage;

    @Schema(description = "异常堆栈(ERROR级别时)")
    private String exceptionStack;

    @Schema(description = "异常类型")
    private String exceptionType;

    @Schema(description = "错误指纹(SHA256哈希)")
    private String errorFingerprint;

    @Schema(description = "操作人")
    private String username;

    @Schema(description = "客户端IP")
    private String clientIp;

    @Schema(description = "请求URI")
    private String requestUri;

    @Schema(description = "HTTP方法")
    private String httpMethod;

    @Schema(description = "HTTP状态码")
    private Integer httpStatus;

    @Schema(description = "耗时(毫秒)")
    private Long costTime;

    @Schema(description = "扩展元数据(JSON)")
    private String metadata;

    @Schema(description = "日志产生时间")
    private LocalDateTime logTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
