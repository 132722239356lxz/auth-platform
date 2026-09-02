package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 当前用户修改密码请求
 */
@Data
@Schema(description = "当前用户修改密码请求")
public class ChangePasswordRequest {

    @NotBlank(message = "原密码不能为空")
    @Schema(description = "原密码")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 100, message = "新密码长度需在6-100字符之间")
    @Schema(description = "新密码")
    private String newPassword;
}
