package com.liang.xz.system.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * <p>AI 调用记录分页查询条件</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Schema(description = "AI 调用记录分页查询条件")
public class AiInvokeLogPageQuery {

    @Schema(description = "页码(从1开始)", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer size = 10;

    @Schema(description = "供应商编码精确匹配")
    private String providerCode;

    @Schema(description = "模型名称模糊匹配")
    private String modelName;

    @Schema(description = "用户ID精确匹配")
    private String userId;

    @Schema(description = "是否成功: true=成功 false=失败")
    private Boolean success;

    @Schema(description = "调用开始时间(yyyy-MM-dd HH:mm:ss)")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @Schema(description = "调用结束时间(yyyy-MM-dd HH:mm:ss)")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
