package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户端查询响应
 *
 * <p>回调地址不再直接挂在客户端响应上，统一由 {@link #subsystems} 提供。
 * 服务端在读取时按需从 {@code oauth2_client_subsystem} 聚合。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "客户端查询响应")
public class ClientResponse {

    @Schema(description = "客户端内部ID")
    private String id;

    @Schema(description = "客户端对外唯一标识")
    private String clientId;

    @Schema(description = "客户端名称")
    private String clientName;

    @Schema(description = "签发时间")
    private LocalDateTime clientIdIssuedAt;

    @Schema(description = "密钥过期时间")
    private LocalDateTime clientSecretExpiresAt;

    @Schema(description = "授权范围")
    private List<String> scopes;

    @Schema(description = "授权模式")
    private List<String> grantTypes;

    @Schema(description = "认证方法")
    private List<String> authMethods;

    @Schema(description = "AccessToken有效期(秒)")
    private Long tokenTtl;

    @Schema(description = "RefreshToken有效期(秒)")
    private Long refreshTtl;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "是否需要用户授权确认")
    private Boolean requireAuthorizationConsent;

    @Schema(description = "子系统列表（统一维护回调地址、图标、描述等）")
    private List<SubsystemResponse> subsystems;
}
