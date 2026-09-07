package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Token吊销请求")
public class RevokeLogRequest {

    @NotBlank(message = "用户ID不能为空")
    @Schema(description = "用户ID", example = "zhangsan", requiredMode = Schema.RequiredMode.REQUIRED)
    private String userId;

    @NotBlank(message = "客户端ID不能为空")
    @Schema(description = "客户端ID", example = "my-app", requiredMode = Schema.RequiredMode.REQUIRED)
    private String clientId;

    @NotNull(message = "吊销类型不能为空")
    @Schema(description = "吊销类型: 1=用户主动登出 2=后台强制下线 3=IAM凭证失效", example = "2")
    private Integer revokeType;

    @Schema(description = "Token类型: ACCESS_TOKEN/REFRESH_TOKEN/ALL", example = "ALL")
    private String tokenType = "ALL";

    @Schema(description = "备注说明")
    private String remark;
}
