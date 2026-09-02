package com.liang.xz.common.core.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>OAuth2 客户端注册实体 —— 映射 oauth2_registered_client 表</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Oauth2Client {

    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "客户端ID(唯一标识)")
    private String clientId;

    @Schema(description = "客户端ID签发时间")
    private LocalDateTime clientIdIssuedAt;

    @Schema(description = "客户端密钥(加密存储)")
    private String clientSecret;

    @Schema(description = "客户端密钥过期时间")
    private LocalDateTime clientSecretExpiresAt;

    @Schema(description = "客户端名称")
    private String clientName;

    @Schema(description = "客户端认证方式")
    private String clientAuthenticationMethods;

    @Schema(description = "授权类型")
    private String authorizationGrantTypes;

    @Schema(description = "回调地址")
    private String redirectUris;

    @Schema(description = "登出回调地址")
    private String postLogoutRedirectUris;

    @Schema(description = "授权范围")
    private String scopes;

    @Schema(description = "客户端配置(JSON)")
    private String clientSettings;

    @Schema(description = "Token配置(JSON)")
    private String tokenSettings;

    @Schema(description = "是否启用(不持久化, 由客户端配置解析)")
    private transient Boolean enabled;
}
