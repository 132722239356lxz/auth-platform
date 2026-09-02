package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "意见反馈请求")
public class FeedbackRequest {

    @NotBlank(message = "反馈内容不能为空")
    @Size(max = 2000, message = "反馈内容不超过2000字符")
    @Schema(description = "反馈内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Schema(description = "联系方式")
    private String contact;

    @Schema(description = "反馈类型: suggestion=建议 bug=缺陷 other=其他", example = "suggestion")
    private String type;
}
