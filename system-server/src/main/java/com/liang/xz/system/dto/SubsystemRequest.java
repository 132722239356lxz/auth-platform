package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 子系统创建/更新请求
 */
@Data
@Schema(description = "子系统创建/更新请求")
public class SubsystemRequest {

    @NotBlank(message = "客户端标识不能为空")
    @Schema(description = "所属客户端标识(clientId)", example = "my-app", requiredMode = Schema.RequiredMode.REQUIRED)
    private String clientId;

    @NotBlank(message = "子系统平台标识不能为空")
    @Pattern(regexp = "web|miniapp|app|desktop|admin", message = "子系统平台标识必须为 web / miniapp / app / desktop / admin")
    @Schema(description = "子系统平台标识（用于区分数据来源）",
            example = "web", allowableValues = {"web", "miniapp", "app", "desktop", "admin"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @NotBlank(message = "子系统名称不能为空")
    @Schema(description = "子系统名称", example = "OA系统", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "图标图片URL", example = "https://cdn.example.com/icons/oa.png")
    private String iconUrl;

    @Schema(description = "回调URL（访问入口地址）", example = "https://oa.example.com")
    private String redirectUri;

    @Schema(description = "子系统描述", example = "企业办公自动化系统")
    private String description;

    @Schema(description = "门户排序号", example = "0")
    private Integer sortOrder;

    @Schema(description = "是否在门户展示", example = "true")
    private Boolean visiblePortal;
}
