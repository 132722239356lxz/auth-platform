package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 审计日志分页查询参数 —— 统一封装多条件筛选
 *
 * @author auth-platform
 * @since 2.0.0
 */
@Data
@Schema(description = "审计日志分页查询参数")
public class AuditPageQuery {

    @Parameter(description = "客户端ID")
    private String clientId;

    @Parameter(description = "用户名")
    private String username;

    @Parameter(description = "吊销类型(1=用户登出,2=强制下线,3=凭证失效)")
    private Integer revokeType;

    @Min(value = 1, message = "页码从1开始")
    @Parameter(description = "页码,从1开始")
    private Integer page = 1;

    @Min(value = 1, message = "每页至少1条")
    @Parameter(description = "每页条数")
    private Integer pageSize = 10;
}
