package com.liang.xz.message.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>消息记录实体 —— 映射 msg_record 表</p>
 * <p>每条发送的消息记录</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageRecord {

    @Schema(description = "主键(自增)")
    private Long id;

    @Schema(description = "消息ID(业务唯一标识)")
    private String messageId;

    @Schema(description = "消息类型: SYSTEM_NOTICE/EVENT_PUSH/APPROVAL_NOTIFY/SUBSYSTEM_COMM")
    private String messageType;

    @Schema(description = "消息标题")
    private String title;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "发送渠道: SMS/EMAIL/IN_APP/WEBSOCKET/MQ, 多个逗号分隔")
    private String channels;

    @Schema(description = "消息状态: PENDING/SENT/FAILED/PARTIAL")
    private String status;

    @Schema(description = "来源系统/模块")
    private String sourceSystem;

    @Schema(description = "发送者")
    private String sender;

    @Schema(description = "接收者(多个逗号分隔)")
    private String receivers;

    @Schema(description = "接收子系统列表(JSON数组, 用于子系统广播)")
    private String targetSubsystems;

    @Schema(description = "业务关联ID(如审批实例ID)")
    private String businessId;

    @Schema(description = "是否已读(站内信)")
    private Boolean isRead;

    @Schema(description = "失败原因")
    private String failReason;

    @Schema(description = "重试次数")
    private Integer retryCount;

    @Schema(description = "模板编码(若使用消息模板发送)")
    private String templateCode;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "发送时间")
    private LocalDateTime sendTime;

    @Schema(description = "阅读时间")
    private LocalDateTime readTime;
}
