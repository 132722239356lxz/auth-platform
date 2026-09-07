package com.liang.xz.system.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>系统公告实体 —— 映射 sys_notice 表</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysNoticeEntity {

    @Schema(description = "公告主键(自增)")
    private Long id;

    @Schema(description = "公告标题")
    private String title;

    @Schema(description = "公告内容")
    private String content;

    @Schema(description = "公告类型: NOTICE=通知, ALERT=告警")
    private String noticeType;

    @Schema(description = "优先级(数值越大越高)")
    private Integer priority;

    @Schema(description = "发布人ID")
    private Long publisherId;

    @Schema(description = "发布人名称")
    private String publisherName;

    @Schema(description = "是否置顶")
    private Boolean top;

    @Schema(description = "发布时间")
    private LocalDateTime publishTime;

    @Schema(description = "过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "状态: true=启用, false=禁用")
    private Boolean enabled;

    @Schema(description = "阅读次数")
    private Long readCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
