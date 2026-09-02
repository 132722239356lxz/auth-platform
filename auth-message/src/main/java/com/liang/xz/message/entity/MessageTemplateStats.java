package com.liang.xz.message.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * <p>消息模板统计信息 —— 联表 msg_record 统计使用次数和最近使用时间</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MessageTemplateStats extends MessageTemplate {

    @Schema(description = "模板使用次数(关联msg_record计数)")
    private Long usageCount;

    @Schema(description = "最近使用时间")
    private LocalDateTime lastUsedTime;
}
