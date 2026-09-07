package com.liang.xz.message.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>消息模板实体 —— 映射 msg_template 表</p>
 * <p>预定义的消息格式模板</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageTemplate {

    @Schema(description = "主键(自增)")
    private Long id;

    @Schema(description = "模板编码(唯一)")
    private String templateCode;

    @Schema(description = "模板名称")
    private String templateName;

    @Schema(description = "模板类型: SMS/EMAIL/IN_APP/WECHAT")
    private String channel;

    @Schema(description = "模板标题(支持变量: {{title}})")
    private String titleTemplate;

    @Schema(description = "模板内容(支持变量: {{applicant}}、{{result}}等)")
    private String contentTemplate;

    @Schema(description = "模板变量说明(JSON数组)")
    private String variables;

    @Schema(description = "状态: 0-停用 1-启用")
    private Integer status;

    @Schema(description = "创建人")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
