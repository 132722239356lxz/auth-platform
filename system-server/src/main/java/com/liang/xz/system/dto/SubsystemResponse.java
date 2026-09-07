package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 子系统查询响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "子系统查询响应")
public class SubsystemResponse {

    @Schema(description = "子系统ID")
    private Long id;

    @Schema(description = "所属客户端标识")
    private String clientId;

    @Schema(description = "客户端名称")
    private String clientName;

    @Schema(description = "子系统平台标识",
            example = "web", allowableValues = {"web", "miniapp", "app", "desktop", "admin"})
    private String code;

    @Schema(description = "子系统名称")
    private String name;

    @Schema(description = "图标图片URL")
    private String iconUrl;

    @Schema(description = "回调URL（访问入口地址）")
    private String redirectUri;

    @Schema(description = "子系统描述")
    private String description;

    @Schema(description = "门户排序号")
    private Integer sortOrder;

    @Schema(description = "是否在门户展示")
    private Boolean visiblePortal;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
