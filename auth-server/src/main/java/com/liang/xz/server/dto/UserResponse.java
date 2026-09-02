package com.liang.xz.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * <p>用户查询响应 DTO</p>
 * <p>安全策略: 不暴露密码，保护用户隐私</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@Schema(description = "用户查询响应")
public class UserResponse {

    @Schema(description = "用户主键ID")
    private Long id;

    @Schema(description = "用户名(登录账号)", example = "zhangsan")
    private String username;

    @Schema(description = "昵称", example = "张三")
    private String nickname;

    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "用户类型", example = "user")
    private String userType;

    @Schema(description = "租户ID", example = "default")
    private String tenantId;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginTime;

    @Schema(description = "最后登录IP")
    private String lastLoginIp;

    @Schema(description = "头像URL")
    private String avatar;
}
