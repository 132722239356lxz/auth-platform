package com.liang.xz.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * <p>登录响应 DTO —— 前后分离的 REST 登录接口返回</p>
 *
 * <p>响应字段:</p>
 * <ul>
 *   <li><b>access_token:</b> JWT AccessToken，前端请求API时放入 Authorization Header</li>
 *   <li><b>refresh_token:</b> 用于刷新过期的 AccessToken</li>
 *   <li><b>token_type:</b> 固定为 Bearer</li>
 *   <li><b>expires_in:</b> AccessToken 过期秒数</li>
 *   <li><b>user_info:</b> 用户基本信息</li>
 *   <li><b>permissions:</b> 用户权限标识列表</li>
 *   <li><b>roles:</b> 用户角色编码列表</li>
 * </ul>
 *
 * <p>注意: permissions 和 roles 仅存放在响应体中，不写入 JWT Token claims，
 * 前端需要自行缓存这些信息用于权限判断。</p>
 *
 * <p>前端使用方式:</p>
 * <pre>
 *   // 请求时带上 Token
 *   axios.defaults.headers.common['Authorization'] = 'Bearer ' + token;
 *
 *   // Token 过期后用 refresh_token 换新
 *   POST /api/auth/refresh { refresh_token: "xxx" }
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@Schema(description = "登录响应(Token + 用户信息)")
public class LoginResponse {

    @Schema(description = "AccessToken(JWT格式)", example = "eyJhbGciOiJSUzI1NiJ9...")
    @JsonProperty("access_token")
    private String accessToken;

    @Schema(description = "RefreshToken(用于刷新AccessToken)", example = "a1b2c3d4...")
    @JsonProperty("refresh_token")
    private String refreshToken;


    @Schema(description = "idToken")
    @JsonProperty("id_token")
    private String idToken;

    @Schema(description = "Token类型", example = "Bearer")
    @JsonProperty("token_type")
    private String tokenType;

    @Schema(description = "AccessToken过期时间(秒)", example = "3600")
    @JsonProperty("expires_in")
    private Long expiresIn;

    @Schema(description = "AccessToken过期时间戳(毫秒)")
    @JsonProperty("expires_at")
    private Long expiresAt;

    @Schema(description = "用户基本信息")
    @JsonProperty("user_info")
    private UserInfo userInfo;

    @Schema(description = "用户权限标识列表(仅存在于响应体，不写入JWT)")
    private List<String> permissions;

    @Schema(description = "用户角色编码列表(仅存在于响应体，不写入JWT)", example = "[\"ROLE_ADMIN\"]")
    private List<String> roles;

    /**
     * 内嵌用户信息
     */
    @Data
    @Builder
    @Schema(description = "登录用户信息")
    public static class UserInfo {

        @Schema(description = "用户ID", example = "1")
        private String userId;

        @Schema(description = "用户名", example = "admin")
        private String username;

        @Schema(description = "昵称", example = "系统管理员")
        private String nickname;

        @Schema(description = "用户类型", example = "admin")
        private String userType;

        @Schema(description = "租户ID", example = "default")
        private String tenantId;

        @Schema(description = "邮箱")
        private String email;

        @Schema(description = "手机号")
        private String phone;
    }
}
