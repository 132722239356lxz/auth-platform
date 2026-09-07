package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 子系统Token分页查询参数 —— 统一封装多条件筛选
 *
 * @author auth-platform
 * @since 2.0.0
 */
@Data
@Schema(description = "子系统Token分页查询参数")
public class SubsystemTokenPageQuery {

    @Parameter(description = "客户端ID")
    private String clientId;

    @Parameter(description = "用户名")
    private String username;

    @Parameter(description = "状态：ACTIVE/EXPIRED/REVOKED/REFRESHED")
    private String status;

    @Parameter(description = "是否过期：true=已过期，false=未过期")
    private Boolean expired;

    @Min(value = 1, message = "页码从1开始")
    @Parameter(description = "页码,从1开始")
    private Integer page = 1;

    @Min(value = 1, message = "每页至少1条")
    @Parameter(description = "每页条数")
    private Integer pageSize = 10;
}
