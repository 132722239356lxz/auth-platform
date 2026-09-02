package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <p>重定向地址项 —— 支持按平台区分回调地址</p>
 *
 * <p>用于客户端注册时配置多来源回调地址，例如:</p>
 * <pre>
 *   { "uri": "https://admin.example.com/callback", "platform": "web",    "label": "管理后台" }
 *   { "uri": "https://m.example.com/callback",      "platform": "mobile", "label": "移动端" }
 * </pre>
 *
 * @author auth-platform
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "重定向地址项")
public class RedirectUriItem {

    @NotBlank(message = "回调地址不能为空")
    @Schema(description = "回调地址URL", example = "https://admin.example.com/callback", requiredMode = Schema.RequiredMode.REQUIRED)
    private String uri;

    @NotBlank(message = "平台标识不能为空")
    @Schema(description = "来源平台: web/mobile/miniapp/desktop", example = "web", requiredMode = Schema.RequiredMode.REQUIRED)
    private String platform;

    @Schema(description = "地址标签说明", example = "管理后台")
    private String label;
}
