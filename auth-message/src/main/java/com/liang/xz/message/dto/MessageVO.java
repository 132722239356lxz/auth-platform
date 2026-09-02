package com.liang.xz.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息VO
 */
@Data
@Builder
@Schema(description = "消息记录")
public class MessageVO {

    private String messageId;
    private String messageType;
    private String title;
    private String content;
    private String channels;
    private String status;
    private String sourceSystem;
    private String sender;
    private String receivers;
    private String targetSubsystems;
    private String businessId;
    private Boolean isRead;
    private String failReason;
    private Integer retryCount;
    private LocalDateTime createTime;
    private LocalDateTime sendTime;
    private LocalDateTime readTime;
}
