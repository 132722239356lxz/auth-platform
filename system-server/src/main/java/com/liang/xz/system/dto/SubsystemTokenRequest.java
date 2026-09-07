package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "子系统Token记录请求")
public class SubsystemTokenRequest {

    @NotBlank(message = "客户端ID不能为空")
    @Schema(description = "客户端ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String clientId;

    @Schema(description = "用户ID")
    private Long userId;

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "AccessToken不能为空")
    @Schema(description = "子系统自签的AccessToken", requiredMode = Schema.RequiredMode.REQUIRED)
    private String accessToken;

    @Schema(description = "子系统自签的RefreshToken")
    private String refreshToken;

    @Schema(description = "Token类型", example = "JWT_BEARER")
    private String tokenType;

    @Schema(description = "AccessToken过期时间", example = "2026-07-11T17:00:00")
    private String accessTokenExpiresAt;

    @Schema(description = "RefreshToken过期时间", example = "2026-07-12T17:00:00")
    private String refreshTokenExpiresAt;

    @Schema(description = "签发IP")
    private String issuedIp;

    @Schema(description = "User-Agent")
    private String userAgent;
}
