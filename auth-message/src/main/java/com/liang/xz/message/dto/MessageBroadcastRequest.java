package com.liang.xz.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 消息广播请求 —— 向多个子系统广播消息
 */
@Data
@Schema(description = "消息广播请求")
public class MessageBroadcastRequest {

    @NotBlank(message = "消息类型不能为空")
    @Schema(description = "消息类型", example = "EVENT_PUSH")
    private String messageType;

    @NotBlank(message = "标题不能为空")
    @Schema(description = "广播标题", example = "用户权限变更通知")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Schema(description = "广播内容(JSON格式)")
    private String content;

    @NotEmpty(message = "目标子系统不能为空")
    @Schema(description = "目标子系统列表", example = "[\"auth-server\",\"system-server\",\"auth-flow\"]")
    private List<String> targetSubsystems;

    @Schema(description = "发送渠道", example = "[\"MQ\",\"WEBSOCKET\"]")
    private List<String> channels;

    @Schema(description = "消息模板编码(若使用模板则根据模板渲染)")
    private String templateCode;

    @Schema(description = "模板变量(用于渲染模板)")
    private Map<String, String> templateVars;

    @Schema(description = "来源系统")
    private String sourceSystem;

    @Schema(description = "事件类型(用于事件推送)", example = "USER_ROLE_CHANGED")
    private String eventType;

    @Schema(description = "业务关联ID")
    private String businessId;

    @Schema(description = "扩展数据")
    private Map<String, Object> extraData;
}
