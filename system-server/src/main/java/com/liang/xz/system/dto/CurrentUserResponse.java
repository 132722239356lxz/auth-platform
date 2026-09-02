package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * <p>当前登录用户信息响应 —— 从JWT Token解析</p>
 *
 * <p>用于前端获取当前登录用户的完整信息(用户名/角色/权限等)</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "当前用户信息")
public class CurrentUserResponse {

    @Schema(description = "用户名(登录账号)", example = "admin")
    private String username;

    @Schema(description = "用户昵称", example = "系统管理员")
    private String nickname;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "租户ID", example = "default")
    private String tenantId;

    @Schema(description = "用户类型", example = "admin")
    private String userType;

    @Schema(description = "部门ID")
    private Long deptId;

    @Schema(description = "部门名称")
    private String deptName;

    @Schema(description = "角色编码列表", example = "[\"ROLE_ADMIN\"]")
    private List<String> roles;

    @Schema(description = "权限标识列表", example = "[\"system:user:list\", \"system:user:add\"]")
    private List<String> permissions;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "邮箱", example = "admin@example.com")
    private String email;
}
