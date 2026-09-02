package com.liang.xz.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>用户信息响应 DTO —— 子系统通过授权码获取用户信息</p>
 *
 * <p>信息维度:</p>
 * <ul>
 *   <li>基本信息: username, nickname, email, phone</li>
 *   <li>扩展信息: userType, tenantId, permissions</li>
 *   <li>OAuth2 Token信息: access_token, refresh_token, token_type, expires_in</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@Schema(description = "用户信息响应(含OAuth2 Token)")
public class UserInfoResponse {

    // ======================== 基本信息 ========================

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名(登录账号)", example = "zhangsan")
    private String username;

    @Schema(description = "昵称", example = "张三")
    private String nickname;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "用户类型: admin/user/service", example = "user")
    private String userType;

    @Schema(description = "租户ID", example = "default")
    private String tenantId;

    // ======================== 权限信息 ========================

    @Schema(description = "用户权限标识列表")
    private List<String> permissions;

    // ======================== OAuth2 Token ========================

    @Schema(description = "OAuth2 AccessToken(JWT)")
    private String accessToken;

    @Schema(description = "OAuth2 RefreshToken")
    private String refreshToken;

    @Schema(description = "Token类型", example = "Bearer")
    private String tokenType;

    @Schema(description = "AccessToken剩余有效时间(秒)", example = "3599")
    private Long expiresIn;

    @Schema(description = "授权范围", example = "openid profile")
    private String scope;

    @Schema(description = "Token签发时间")
    private LocalDateTime issuedAt;

    @Schema(description = "AccessToken过期时间")
    private LocalDateTime expiresAt;
}
