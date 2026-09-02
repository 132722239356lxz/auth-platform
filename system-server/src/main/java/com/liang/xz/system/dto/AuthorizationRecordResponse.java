package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "授权记录响应")
public class AuthorizationRecordResponse {

    @Schema(description = "授权记录ID")
    private String id;

    @Schema(description = "客户端ID")
    private String clientId;

    @Schema(description = "客户端名称")
    private String clientName;

    @Schema(description = "授权用户名")
    private String principalName;

    @Schema(description = "授权模式")
    private String authorizationGrantType;

    @Schema(description = "授权Scope")
    private List<String> authorizedScopes;

    @Schema(description = "AccessToken脱敏")
    private String accessTokenSnip;

    @Schema(description = "AccessToken签发时间")
    private LocalDateTime accessTokenIssuedAt;

    @Schema(description = "AccessToken过期时间")
    private LocalDateTime accessTokenExpiresAt;

    @Schema(description = "AccessToken是否已过期")
    private Boolean accessTokenExpired;

    @Schema(description = "RefreshToken脱敏")
    private String refreshTokenSnip;

    @Schema(description = "RefreshToken签发时间")
    private LocalDateTime refreshTokenIssuedAt;

    @Schema(description = "RefreshToken过期时间")
    private LocalDateTime refreshTokenExpiresAt;

    @Schema(description = "RefreshToken是否已过期")
    private Boolean refreshTokenExpired;

    @Schema(description = "授权码脱敏")
    private String authorizationCodeSnip;

    @Schema(description = "授权码过期时间")
    private LocalDateTime authorizationCodeExpiresAt;
}
