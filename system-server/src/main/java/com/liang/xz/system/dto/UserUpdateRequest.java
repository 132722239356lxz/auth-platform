package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "管理员修改用户请求")
public class UserUpdateRequest {

    @Size(min = 3, max = 50, message = "用户名长度需在3-50字符之间")
    @Schema(description = "用户名")
    private String username;

    @Size(min = 6, max = 100, message = "密码长度需在6-100字符之间")
    @Schema(description = "密码(不填则不修改)")
    private String password;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "用户类型", example = "user")
    private String userType;

    @Schema(description = "部门ID")
    private Long deptId;
}
