package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 公告创建/更新请求
 */
@Data
@Schema(description = "公告请求")
public class NoticeRequest {

    @NotBlank(message = "公告标题不能为空")
    @Size(max = 100, message = "标题长度不能超过100字符")
    @Schema(description = "公告标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @NotBlank(message = "公告内容不能为空")
    @Schema(description = "公告内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Schema(description = "公告类型：ANNOUNCEMENT/NOTICE/WARNING/MAINTAIN")
    private String noticeType;

    @Schema(description = "优先级：1=普通 2=重要 3=紧急")
    private Integer priority;

    @Schema(description = "是否置顶")
    private Boolean top;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "发布时间")
    private LocalDateTime publishTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
