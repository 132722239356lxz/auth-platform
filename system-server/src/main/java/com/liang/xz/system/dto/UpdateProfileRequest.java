package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 当前用户更新个人资料请求
 */
@Data
@Schema(description = "当前用户更新个人资料请求")
public class UpdateProfileRequest {

    @Size(max = 20, message = "昵称长度不能超过20字符")
    @Schema(description = "昵称")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100字符")
    @Schema(description = "邮箱")
    private String email;

    @Size(max = 20, message = "手机号长度不能超过20字符")
    @Schema(description = "手机号")
    private String phone;

    @Size(max = 50000, message = "头像长度不能超过50000字符")
    @Schema(description = "头像URL或Base64")
    private String avatar;
}
