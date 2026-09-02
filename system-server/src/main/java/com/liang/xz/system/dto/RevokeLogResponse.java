package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "吊销日志响应")
public class RevokeLogResponse {

    @Schema(description = "日志ID")
    private Long id;

    @Schema(description = "用户ID")
    private String userId;

    @Schema(description = "客户端ID")
    private String clientId;

    @Schema(description = "客户端名称")
    private String clientName;

    @Schema(description = "Token类型")
    private String tokenType;

    @Schema(description = "Token脱敏片段")
    private String tokenSnip;

    @Schema(description = "吊销类型")
    private Integer revokeType;

    @Schema(description = "吊销类型描述")
    private String revokeTypeDesc;

    @Schema(description = "吊销时间")
    private LocalDateTime createTime;

    @Schema(description = "备注")
    private String remark;
}
