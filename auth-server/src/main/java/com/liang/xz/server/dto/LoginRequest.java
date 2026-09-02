package com.liang.xz.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>登录请求 DTO —— 前后分离的 REST 登录接口</p>
 *
 * <p>支持三种登录方式:</p>
 * <ul>
 *   <li>手机号+密码登录: phone + password + loginType=PASSWORD</li>
 *   <li>用户名+密码登录: username + password + loginType=PASSWORD</li>
 *   <li>手机号+短信验证码登录: phone + smsCode + loginType=SMS（需短信服务就绪）</li>
 * </ul>
 *
 * <p>规则: phone 和 username 至少提供一个，clientId 决定前台入口(admin-web 拦截普通用户)。</p>
 *
 * <p>适用场景:</p>
 * <ul>
 *   <li>Vue/React 前端单页应用直接登录</li>
 *   <li>移动端 APP 登录</li>
 *   <li>第三方系统通过API集成登录</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Schema(description = "登录请求")
public class LoginRequest {

    @Schema(description = "手机号(手机号登录时填写，短信验证码登录必填)", example = "13800138000")
    private String phone;

    @Schema(description = "用户名(用户名登录时填写)", example = "admin")
    private String username;

    @Schema(description = "密码(明文)，密码登录时必填", example = "admin123")
    private String password;

    @Schema(description = "短信验证码，短信验证码登录时填写")
    private String smsCode;

    @Schema(description = "登录方式: PASSWORD(密码登录) / SMS(短信验证码登录)，默认PASSWORD", example = "PASSWORD")
    private String loginType = "PASSWORD";

    @Schema(description = "客户端ID(前端应用标识)，默认 admin-web", example = "admin-web")
    private String clientId = "admin-web";

    @Schema(description = "租户ID(多租户场景)", example = "default")
    private String tenantId = "default";
}
