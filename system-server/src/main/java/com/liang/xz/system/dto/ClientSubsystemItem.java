package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 客户端子系统条目（客户端编辑时内联使用）
 *
 * <p>一个客户端下可挂多个子系统，
 * 子系统是门户入口的最小单元：每个子系统独立维护名称、图标、回调 URL、描述、平台标识等。</p>
 *
 * <p>与 {@link SubsystemRequest} 的区别：</p>
 * <ul>
 *     <li>本类由客户端编辑表单内联提交，{@code clientId} 由后端从父客户端注入</li>
 *     <li>{@code id} 为空表示新增；非空表示更新已有子系统</li>
 * </ul>
 */
@Data
@Schema(description = "客户端子系统条目（编辑时内联提交）")
public class ClientSubsystemItem {

    @Schema(description = "子系统ID（新增时为空）")
    private Long id;

    @NotBlank(message = "子系统平台标识不能为空")
    @Pattern(regexp = "web|miniapp|app|desktop|admin", message = "子系统平台标识必须为 web / miniapp / app / desktop / admin")
    @Schema(description = "子系统平台标识（用于区分数据来源）",
            example = "web", allowableValues = {"web", "miniapp", "app", "desktop", "admin"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @NotBlank(message = "子系统名称不能为空")
    @Schema(description = "子系统名称", example = "个人博客 Web", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "图标图片URL", example = "https://cdn.example.com/icons/blog.png")
    private String iconUrl;

    @Schema(description = "回调URL（子系统的访问入口域名，也是 OAuth2 回调地址）",
            example = "http://127.0.0.1:9005/oauth2/callback")
    private String redirectUri;

    @Schema(description = "子系统描述", example = "个人技术博客系统")
    private String description;

    @Schema(description = "门户排序号（越小越靠前）", example = "0")
    private Integer sortOrder;

    @Schema(description = "是否在门户展示", example = "true")
    private Boolean visiblePortal;
}
