package com.liang.xz.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * <p>用户注册请求 DTO</p>
 *
 * <p>手机号作为主要登录凭证，注册时必须提供唯一手机号。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Schema(description = "用户注册请求")
public class UserRegisterRequest {

    @Schema(description = "用户名(展示用，非登录凭证)", example = "zhangsan")
    @Size(min = 3, max = 50, message = "用户名长度需在3-50字符之间")
    private String username;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号(登录账号)", example = "13800138000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度需在6-100字符之间")
    @Schema(description = "密码(明文,入库前BCrypt编码)", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Schema(description = "昵称", example = "张三")
    private String nickname;

    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;

    @Schema(description = "用户类型", example = "user", allowableValues = {"admin", "user", "service"})
    private String userType = "user";

    @Schema(description = "租户ID", example = "default")
    private String tenantId = "default";
}
