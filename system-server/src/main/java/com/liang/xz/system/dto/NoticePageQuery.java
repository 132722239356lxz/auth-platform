package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 公告查询参数
 */
@Data
@Schema(description = "公告查询参数")
public class NoticePageQuery {

    @Schema(description = "关键词（标题/内容）")
    private String keyword;

    @Schema(description = "公告类型")
    private String noticeType;

    @Schema(description = "状态：true=启用，false=禁用")
    private Boolean enabled;

    @Min(value = 1, message = "页码从1开始")
    @Schema(description = "页码", example = "1")
    private Integer page = 1;

    @Min(value = 1, message = "每页至少1条")
    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;
}
