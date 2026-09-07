package com.liang.xz.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <p>系统仪表盘统计响应</p>
 *
 * @author auth-platform
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "仪表盘统计数据")
public class DashboardResponse {

    @Schema(description = "用户总数", example = "25")
    private long userCount;

    @Schema(description = "角色总数", example = "5")
    private long roleCount;

    @Schema(description = "菜单总数", example = "30")
    private long menuCount;

    @Schema(description = "部门总数", example = "8")
    private long deptCount;

    @Schema(description = "字典类型总数", example = "10")
    private long dictTypeCount;

    @Schema(description = "字典数据总数", example = "45")
    private long dictDataCount;

    @Schema(description = "OAuth2客户端总数", example = "3")
    private long clientCount;

    @Schema(description = "子系统活跃Token数量", example = "8")
    private long subsystemActiveTokenCount;

    @Schema(description = "OAuth2系统活跃Token数量", example = "5")
    private long systemActiveTokenCount;

    @Schema(description = "待审批数量", example = "3")
    private long pendingApprovalCount;

    @Schema(description = "活跃Token总数 (系统 + 子系统)", example = "13")
    private long activeTokenCount;

    @Schema(description = "活跃Token数量(别名兼容)", example = "13")
    public long getActiveTokens() {
        return this.activeTokenCount;
    }
}
