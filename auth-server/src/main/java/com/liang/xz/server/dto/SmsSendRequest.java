package com.liang.xz.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>短信验证码发送请求</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Schema(description = "短信验证码发送请求")
public class SmsSendRequest {

    @Schema(description = "手机号", example = "13800138000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String phone;
}
