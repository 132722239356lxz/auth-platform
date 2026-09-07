package com.liang.xz.system.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>意见反馈实体 —— 映射 sys_feedback 表</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackEntity {

    @Schema(description = "反馈主键(自增)")
    private Long id;

    @Schema(description = "反馈人用户名")
    private String username;

    @Schema(description = "反馈内容")
    private String content;

    @Schema(description = "联系方式")
    private String contact;

    @Schema(description = "反馈类型: BUG=缺陷, FEATURE=功能建议, OTHER=其他")
    private String type;

    @Schema(description = "处理状态: PENDING=待处理, PROCESSING=处理中, RESOLVED=已解决")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
