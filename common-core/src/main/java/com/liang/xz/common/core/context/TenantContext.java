package com.liang.xz.common.core.context;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * <p>租户上下文 —— 基于 TransmittableThreadLocal 的租户信息传递</p>
 *
 * <p>使用场景:</p>
 * <ul>
 *   <li>JWT Token 解析出 tenantId 后设置到上下文</li>
 *   <li>业务代码通过静态方法获取当前租户</li>
 *   <li>请求结束后自动清理(通过 Filter 或 Interceptor)</li>
 *   <li>支持线程池场景下自动透传(使用 TTL 包装的线程池)</li>
 * </ul>
 *
 * <p>典型用法:</p>
 * <pre>
 *   // 请求入口 Filter 中设置
 *   TenantContext.setTenantId("tenant-001");
 *   // 业务代码中获取
 *   String tenantId = TenantContext.getTenantId();
 *   // 请求结束后清理
 *   TenantContext.clear();
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public final class TenantContext {

    private static final TransmittableThreadLocal<String> TENANT_HOLDER = new TransmittableThreadLocal<>();

    private TenantContext() {
        // 工具类禁止实例化
    }

    /**
     * 设置当前线程的租户ID
     *
     * @param tenantId 租户唯一标识
     */
    public static void setTenantId(String tenantId) {
        TENANT_HOLDER.set(tenantId);
    }

    /**
     * 获取当前线程的租户ID
     *
     * @return 租户ID，可能为 null(未设置时)
     */
    public static String getTenantId() {
        return TENANT_HOLDER.get();
    }

    /**
     * 获取当前线程的租户ID，如果未设置则返回默认值
     *
     * @param defaultTenantId 默认租户ID
     * @return 租户ID
     */
    public static String getTenantIdOrDefault(String defaultTenantId) {
        String tenantId = TENANT_HOLDER.get();
        return tenantId != null ? tenantId : defaultTenantId;
    }

    /**
     * 清除当前线程的租户ID(防止内存泄漏)
     */
    public static void clear() {
        TENANT_HOLDER.remove();
    }
}
