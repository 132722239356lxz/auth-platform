package com.liang.xz.aiagent.dto;

import com.liang.xz.aiagent.entity.UploadTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>上传任务 VO</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadTaskVO {
    private String id;
    private Long docId;
    private String kbName;
    private String fileName;
    private Long fileSize;
    private String status;
    private Integer progress;
    private String errorMsg;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UploadTaskVO from(UploadTask task) {
        return UploadTaskVO.builder()
                .id(task.getId())
                .docId(task.getDocId())
                .kbName(task.getKbName())
                .fileName(task.getFileName())
                .fileSize(task.getFileSize())
                .status(task.getStatus())
                .progress(task.getProgress())
                .errorMsg(task.getErrorMsg())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
