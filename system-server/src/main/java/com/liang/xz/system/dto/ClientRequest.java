package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户端注册/更新请求
 *
 * <p>OAuth2 回调地址不再由本类直接维护，
 * 改由 {@link #subsystems} 中每个子系统条目提供，
 * 后端在保存时自动从子系统聚合出 {@code oauth2_registered_client.redirect_uris}。</p>
 */
@Data
@Schema(description = "客户端注册/更新请求")
public class ClientRequest {

    @NotBlank(message = "客户端标识不能为空")
    @Schema(description = "客户端对外唯一标识(clientId)", example = "my-app", requiredMode = Schema.RequiredMode.REQUIRED)
    private String clientId;

    @Schema(description = "客户端密钥(明文)，不传则自动生成", example = "my-secret")
    private String clientSecret;

    @NotBlank(message = "客户端名称不能为空")
    @Schema(description = "客户端名称", example = "我的应用", requiredMode = Schema.RequiredMode.REQUIRED)
    private String clientName;

    @NotEmpty(message = "授权模式不能为空")
    @Schema(description = "授权模式列表", example = "[\"authorization_code\",\"refresh_token\"]")
    private List<String> grantTypes;

    @Schema(description = "认证方法列表，不传默认 client_secret_basic + client_secret_post", example = "[\"client_secret_basic\",\"client_secret_post\"]")
    private List<String> authMethods;

    @Schema(description = "Scope列表，不传默认 openid", example = "[\"openid\",\"profile\",\"read\"]")
    private List<String> scopes;

    @Valid
    @Schema(description = "子系统列表（每个子系统独立维护回调地址、图标、描述等门户信息）")
    private List<ClientSubsystemItem> subsystems;

    @Schema(description = "是否启用")
    private Boolean enabled = true;

    @Schema(description = "是否需要授权确认页面(consent page)，默认 false（信任的内部应用可跳过授权确认）", example = "false")
    private Boolean requireAuthorizationConsent = false;

    @Schema(description = "密钥过期时间")
    private LocalDateTime clientSecretExpiresAt;

    @Schema(description = "AccessToken有效期(秒)", example = "3600")
    private Long tokenTtl;

    @Schema(description = "RefreshToken有效期(秒)", example = "43200")
    private Long refreshTtl;
}
