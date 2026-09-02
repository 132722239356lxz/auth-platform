package com.liang.xz.server.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>平台用户实体 —— 映射 sys_user 表</p>
 *
 * <p>核心职责:</p>
 * <ul>
 *   <li>存储授权平台的用户账号信息</li>
 *   <li>密码使用 BCrypt 编码存储</li>
 *   <li>支持账号启用/禁用控制</li>
 *   <li>支持多租户标识</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {

    @Schema(description = "用户主键(自增)")
    private Long id;

    @Schema(description = "用户名(登录账号，唯一)")
    private String username;

    @Schema(description = "密码(BCrypt编码)")
    private String password;

    @Schema(description = "用户昵称/显示名称")
    private String nickname;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "用户类型: admin=管理员, user=普通用户, service=服务账号")
    private String userType;

    @Schema(description = "租户ID(多租户隔离)")
    private String tenantId;

    @Schema(description = "账号状态: true=启用, false=禁用")
    private Boolean enabled;

    @Schema(description = "账号是否未过期")
    private Boolean accountNonExpired;

    @Schema(description = "账号是否未锁定")
    private Boolean accountNonLocked;

    @Schema(description = "密码是否未过期")
    private Boolean credentialsNonExpired;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginTime;

    @Schema(description = "最后登录IP")
    private String lastLoginIp;

    @Schema(description = "头像URL")
    private String avatar;
}
