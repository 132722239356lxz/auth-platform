package com.liang.xz.system.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>令牌吊销日志实体 —— 映射 sys_token_revoke_log 表</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenRevokeLog {

    @Schema(description = "主键(自增)")
    private Long id;

    @Schema(description = "用户ID")
    private String userId;

    @Schema(description = "客户端ID")
    private String clientId;

    @Schema(description = "令牌类型")
    private String tokenType;

    @Schema(description = "令牌摘要(脱敏)")
    private String tokenSnip;

    @Schema(description = "吊销类型码")
    private Integer revokeType;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "备注")
    private String remark;
}
