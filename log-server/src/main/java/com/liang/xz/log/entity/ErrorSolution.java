package com.liang.xz.log.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * <p>错误解决方案实体 —— 映射 sys_error_solution 表</p>
 * <p>将已验证的解决方案沉淀下来, 相同指纹的错误自动关联, 实现"自学习"效果</p>
 *
 * @author liang
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_error_solution")
public class ErrorSolution {

    @Id
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "关联的错误指纹")
    private String errorFingerprint;

    @Schema(description = "错误特征描述(异常类型+关键信息)")
    private String errorPattern;

    @Schema(description = "错误根因分析")
    private String rootCause;

    @Schema(description = "解决方案")
    private String solution;

    @Schema(description = "解决方案详细步骤")
    private String steps;

    @Schema(description = "关联文档/链接")
    private String referenceUrl;

    @Schema(description = "解决的次数(自动计数)")
    private Integer resolveCount;

    @Schema(description = "状态: DRAFT/PUBLISHED/ARCHIVED")
    private String status;

    @Schema(description = "创建人")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
