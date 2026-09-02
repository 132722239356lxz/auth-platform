package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "子系统Token刷新请求")
public class TokenRefreshRequest {

    @NotBlank(message = "客户端ID不能为空")
    @Schema(description = "客户端ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String clientId;

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "旧RefreshToken不能为空")
    @Schema(description = "旧RefreshToken", requiredMode = Schema.RequiredMode.REQUIRED)
    private String oldRefreshToken;

    @NotBlank(message = "新AccessToken不能为空")
    @Schema(description = "新AccessToken", requiredMode = Schema.RequiredMode.REQUIRED)
    private String newAccessToken;

    @NotBlank(message = "新RefreshToken不能为空")
    @Schema(description = "新RefreshToken", requiredMode = Schema.RequiredMode.REQUIRED)
    private String newRefreshToken;

    @Schema(description = "AccessToken过期时间")
    private String accessTokenExpiresAt;

    @Schema(description = "RefreshToken过期时间")
    private String refreshTokenExpiresAt;

    @Schema(description = "签发IP")
    private String issuedIp;
}
