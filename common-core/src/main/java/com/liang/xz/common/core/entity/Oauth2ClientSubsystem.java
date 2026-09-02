package com.liang.xz.common.core.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 客户端子系统实体 —— 映射 oauth2_client_subsystem 表
 * <p>
 * 一个客户端可配置多个子系统，每个子系统有独立的图标、URL、描述、平台标识等门户展示信息。
 * </p>
 *
 * <p>v1.7 新增 {@code code} 字段，用于标识子系统所属平台（web / miniapp / app / desktop / admin），
 * 业务方可按 code 区分数据来源，例如对 Web 端和小程序端分别下发不同的数据范围。</p>
 *
 * @author auth-platform
 * @since 1.5.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Oauth2ClientSubsystem {

    private Long id;

    /** 关联的客户端标识(client_id) */
    private String clientId;

    /**
     * 平台标识 / 子系统编码
     * <p>用于区分数据来源平台，取值示例：</p>
     * <ul>
     *     <li>{@code web}      —— Web 端（浏览器 / 后台管理）</li>
     *     <li>{@code miniapp}  —— 小程序端（微信/支付宝等）</li>
     *     <li>{@code app}      —— 移动 App 端</li>
     *     <li>{@code desktop}  —— 桌面客户端</li>
     *     <li>{@code admin}    —— 管理后台</li>
     * </ul>
     */
    private String code;

    /** 子系统名称 */
    private String name;

    /** 子系统图标URL */
    private String iconUrl;

    /** 回调URL（即子系统的访问入口域名） */
    private String redirectUri;

    /** 子系统描述 */
    private String description;

    /** 门户排序号（越小越靠前） */
    private Integer sortOrder;

    /** 是否在门户展示 */
    private Boolean visiblePortal;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
