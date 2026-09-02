package com.liang.xz.common.core.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.Map;

/**
 * <p>动态数据源路由 —— 根据 DataSourceContext 动态切换数据源</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public class DynamicDataSource extends AbstractRoutingDataSource {

    public DynamicDataSource(DataSource primaryDataSource, Map<String, DataSource> secondaryDataSources) {
        // 默认数据源
        super.setDefaultTargetDataSource(primaryDataSource);

        // 合并所有数据源: primary + secondary
        Map<Object, Object> targetDataSources = new java.util.HashMap<>();
        targetDataSources.put(DataSourceContext.PRIMARY, primaryDataSource);
        if (secondaryDataSources != null) {
            targetDataSources.putAll(secondaryDataSources);
        }
        super.setTargetDataSources(targetDataSources);

        // 初始化
        super.afterPropertiesSet();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceContext.get();
    }
}
