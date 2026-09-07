package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 公告响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "公告响应")
public class NoticeResponse {

    @Schema(description = "公告ID")
    private Long id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "公告类型")
    private String noticeType;

    @Schema(description = "优先级")
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

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "阅读次数")
    private Long readCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
