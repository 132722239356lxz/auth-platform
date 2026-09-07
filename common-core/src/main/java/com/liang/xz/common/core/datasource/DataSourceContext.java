package com.liang.xz.common.core.datasource;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * <p>数据源路由上下文 —— 基于 TransmittableThreadLocal 支持线程池透传</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public final class DataSourceContext {

    /** 主数据源标识 */
    public static final String PRIMARY = "primary";

    private static final TransmittableThreadLocal<String> DATASOURCE_HOLDER = new TransmittableThreadLocal<>();

    private DataSourceContext() {
    }

    /**
     * 设置当前线程使用的数据源名称
     */
    public static void set(String dataSourceName) {
        DATASOURCE_HOLDER.set(dataSourceName);
    }

    /**
     * 获取当前数据源名称（默认返回 primary）
     */
    public static String get() {
        String ds = DATASOURCE_HOLDER.get();
        return ds != null ? ds : PRIMARY;
    }

    /**
     * 切换为从数据源
     */
    public static void useSecondary(String name) {
        DATASOURCE_HOLDER.set(name);
    }

    /**
     * 切换回主数据源
     */
    public static void usePrimary() {
        DATASOURCE_HOLDER.set(PRIMARY);
    }

    /**
     * 清除数据源上下文
     */
    public static void clear() {
        DATASOURCE_HOLDER.remove();
    }
}
