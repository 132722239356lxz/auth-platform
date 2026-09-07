package com.liang.xz.flow.dto;

import com.liang.xz.flow.enums.ApplyType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * <p>权限申请内容 —— 工作流 applyContent 的 JSON 结构</p>
 *
 * <p>当申请内容为该结构(含 roleIds)且流程最终审批通过时,
 * {@code PermissionGrantService} 会为目标用户赋予对应角色(权限)。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Schema(description = "权限申请内容(applyContent JSON结构)")
public class PermissionApplyContent {

    /**
     * 申请场景类型，用于审批通过后路由到不同赋权处理器。
     * 默认 {@link ApplyType#ROLE}（兼容历史角色权限申请）。
     */
    @Schema(description = "申请场景类型：ROLE=角色权限申请；SUBSYSTEM_VISIBILITY=子系统可见权限申请",
            example = "ROLE")
    private String type = ApplyType.ROLE.name();

    @Schema(description = "目标用户名", example = "zhangsan")
    private String targetUsername;

    @Schema(description = "目标用户ID", example = "2")
    private Long targetUserId;

    @Schema(description = "要赋予的角色ID列表", example = "[1]")
    private List<Long> roleIds;

    @Schema(description = "申请说明 / 子系统可见申请的理由", example = "申请管理员权限")
    private String remark;
}
