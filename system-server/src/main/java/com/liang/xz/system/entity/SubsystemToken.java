package com.liang.xz.system.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>子系统Token记录实体 —— 映射 subsystem_token 表</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubsystemToken {

    @Schema(description = "主键(自增)")
    private Long id;

    @Schema(description = "客户端ID")
    private String clientId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "Access Token")
    private String accessToken;

    @Schema(description = "Refresh Token")
    private String refreshToken;

    @Schema(description = "Token类型")
    private String tokenType;

    @Schema(description = "Access Token过期时间")
    private LocalDateTime accessTokenExpiresAt;

    @Schema(description = "Refresh Token过期时间")
    private LocalDateTime refreshTokenExpiresAt;

    @Schema(description = "状态: ACTIVE=有效, REVOKED=已吊销, EXPIRED=已过期")
    private String status;

    @Schema(description = "父Token ID(用于Token刷新链追踪)")
    private Long parentTokenId;

    @Schema(description = "签发IP地址")
    private String issuedIp;

    @Schema(description = "用户代理(User-Agent)")
    private String userAgent;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "最后刷新时间")
    private LocalDateTime lastRefreshTime;

    @Schema(description = "刷新次数")
    private Integer refreshCount;

    @Schema(description = "吊销时间")
    private LocalDateTime revokeTime;

    @Schema(description = "吊销原因码")
    private Integer revokeReason;

    @Schema(description = "备注")
    private String remark;
}
