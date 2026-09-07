package com.liang.xz.common.core.datasource;

import java.lang.annotation.*;

/**
 * <p>数据源切换注解 —— 标注在方法上自动切换数据源</p>
 *
 * <pre>
 * // 切换到从库
 * {@code @TargetDataSource("log-db")}
 * public void saveLog(LogEntity log) { ... }
 *
 * // 切换回主库（默认）
 * {@code @TargetDataSource(DataSourceContext.PRIMARY)}
 * public User getUser(Long id) { ... }
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TargetDataSource {

    /**
     * 数据源名称（默认主库 primary）
     */
    String value() default DataSourceContext.PRIMARY;
}
