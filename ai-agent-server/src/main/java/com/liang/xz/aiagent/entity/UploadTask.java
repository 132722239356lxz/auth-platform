package com.liang.xz.aiagent.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>文件上传任务实体 —— 映射 ai_upload_task 表, 用于持久化上传/处理进度</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadTask {

    @Schema(description = "任务ID(UUID)")
    private String id;

    @Schema(description = "关联的文档ID")
    private Long docId;

    @Schema(description = "目标知识库名称")
    private String kbName;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "文件大小(字节)")
    private Long fileSize;

    @Schema(description = "处理状态: PENDING/PROCESSING/COMPLETED/FAILED")
    private String status;

    @Schema(description = "处理进度(0-100)")
    private Integer progress;

    @Schema(description = "错误信息")
    private String errorMsg;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
