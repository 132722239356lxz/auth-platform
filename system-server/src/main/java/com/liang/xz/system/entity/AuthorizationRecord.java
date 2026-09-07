package com.liang.xz.system.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>授权记录实体 —— 映射 oauth2_authorization 表</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizationRecord {

    @Schema(description = "授权记录ID")
    private String id;

    @Schema(description = "注册客户端ID")
    private String registeredClientId;

    @Schema(description = "授权主体名称(用户名)")
    private String principalName;

    @Schema(description = "授权类型")
    private String authorizationGrantType;

    @Schema(description = "授权范围")
    private String authorizedScopes;

    @Schema(description = "额外属性(JSON)")
    private String attributes;

    @Schema(description = "授权状态")
    private String state;

    @Schema(description = "授权码值")
    private String authorizationCodeValue;

    @Schema(description = "授权码签发时间")
    private LocalDateTime authorizationCodeIssuedAt;

    @Schema(description = "授权码过期时间")
    private LocalDateTime authorizationCodeExpiresAt;

    @Schema(description = "授权码元数据")
    private String authorizationCodeMetadata;

    @Schema(description = "Access Token值")
    private String accessTokenValue;

    @Schema(description = "Access Token签发时间")
    private LocalDateTime accessTokenIssuedAt;

    @Schema(description = "Access Token过期时间")
    private LocalDateTime accessTokenExpiresAt;

    @Schema(description = "Access Token元数据")
    private String accessTokenMetadata;

    @Schema(description = "Refresh Token值")
    private String refreshTokenValue;

    @Schema(description = "Refresh Token签发时间")
    private LocalDateTime refreshTokenIssuedAt;

    @Schema(description = "Refresh Token过期时间")
    private LocalDateTime refreshTokenExpiresAt;

    @Schema(description = "Refresh Token元数据")
    private String refreshTokenMetadata;

    @Schema(description = "OIDC ID Token值")
    private String oidcIdTokenValue;

    @Schema(description = "OIDC ID Token签发时间")
    private LocalDateTime oidcIdTokenIssuedAt;

    @Schema(description = "OIDC ID Token过期时间")
    private LocalDateTime oidcIdTokenExpiresAt;

    @Schema(description = "OIDC ID Token元数据")
    private String oidcIdTokenMetadata;

    @Schema(description = "设备码值")
    private String deviceCodeValue;

    @Schema(description = "设备码签发时间")
    private LocalDateTime deviceCodeIssuedAt;

    @Schema(description = "设备码过期时间")
    private LocalDateTime deviceCodeExpiresAt;

    @Schema(description = "设备码元数据")
    private String deviceCodeMetadata;

    @Schema(description = "用户码值")
    private String userCodeValue;

    @Schema(description = "用户码签发时间")
    private LocalDateTime userCodeIssuedAt;

    @Schema(description = "用户码过期时间")
    private LocalDateTime userCodeExpiresAt;

    @Schema(description = "用户码元数据")
    private String userCodeMetadata;
}
