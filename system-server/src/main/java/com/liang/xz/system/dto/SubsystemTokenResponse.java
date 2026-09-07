package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "子系统Token查询响应")
public class SubsystemTokenResponse {

    @Schema(description = "Token记录ID")
    private Long id;

    @Schema(description = "客户端ID")
    private String clientId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "AccessToken脱敏")
    private String accessTokenSnip;

    @Schema(description = "RefreshToken脱敏")
    private String refreshTokenSnip;

    @Schema(description = "Token类型")
    private String tokenType;

    @Schema(description = "AccessToken过期时间")
    private LocalDateTime accessTokenExpiresAt;

    @Schema(description = "RefreshToken过期时间")
    private LocalDateTime refreshTokenExpiresAt;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "是否已过期")
    private Boolean expired;

    @Schema(description = "父Token ID")
    private Long parentTokenId;

    @Schema(description = "签发IP")
    private String issuedIp;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "最后刷新时间")
    private LocalDateTime lastRefreshTime;

    @Schema(description = "刷新次数")
    private Integer refreshCount;

    @Schema(description = "吊销时间")
    private LocalDateTime revokeTime;

    @Schema(description = "吊销原因描述")
    private String revokeReasonDesc;
}
