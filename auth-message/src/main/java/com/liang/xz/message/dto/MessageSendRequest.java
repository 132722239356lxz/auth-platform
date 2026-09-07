package com.liang.xz.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 消息发送请求
 */
@Data
@Schema(description = "消息发送请求")
public class MessageSendRequest {

    @NotBlank(message = "消息类型不能为空")
    @Schema(description = "消息类型: SYSTEM_NOTICE/EVENT_PUSH/APPROVAL_NOTIFY/SUBSYSTEM_COMM/USER_MESSAGE")
    private String messageType;

    @NotBlank(message = "标题不能为空")
    @Schema(description = "消息标题")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "发送渠道: SMS/EMAIL/IN_APP/WEBSOCKET/MQ", example = "[\"IN_APP\",\"WEBSOCKET\"]")
    private List<String> channels;

    @Schema(description = "来源系统")
    private String sourceSystem;

    @Schema(description = "发送者")
    private String sender;

    @Schema(description = "接收者列表")
    private List<String> receivers;

    @Schema(description = "业务关联ID")
    private String businessId;

    @Schema(description = "消息模板编码(若使用模板则根据模板渲染)")
    private String templateCode;

    @Schema(description = "模板变量(用于渲染模板)")
    private Map<String, String> templateVars;
}
