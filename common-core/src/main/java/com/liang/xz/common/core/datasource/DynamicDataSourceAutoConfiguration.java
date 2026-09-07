package com.liang.xz.common.core.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>多数据源自动配置</p>
 *
 * <p>启用条件: spring.datasource.primary.url 配置存在</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass({HikariDataSource.class})
@EnableConfigurationProperties(MultiDataSourceProperties.class)
@ConditionalOnProperty(prefix = "spring.datasource.primary", name = "url")
public class DynamicDataSourceAutoConfiguration {

    private final MultiDataSourceProperties properties;

    public DynamicDataSourceAutoConfiguration(MultiDataSourceProperties properties) {
        this.properties = properties;
    }

    /**
     * 动态数据源（聚合主库 + 所有从库）
     */
    @Bean
    @Primary
    public DataSource dynamicDataSource() {
        DataSource primary = createDataSource(properties.getPrimary());
        Map<String, DataSource> secondaries = new HashMap<>();
        if (properties.getSecondary() != null) {
            properties.getSecondary().forEach((name, config) -> {
                secondaries.put(name, createDataSource(config));
            });
        }
        return new DynamicDataSource(primary, secondaries);
    }

    /**
     * 主库 JdbcTemplate
     */
    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dynamicDataSource) {
        return new JdbcTemplate(dynamicDataSource);
    }

    /**
     * 主库事务管理器
     */
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(DataSource dynamicDataSource) {
        return new DataSourceTransactionManager(dynamicDataSource);
    }

    /**
     * 根据配置创建 HikariDataSource
     */
    private DataSource createDataSource(MultiDataSourceProperties.DataSourceConfig config) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(config.getUrl());
        ds.setUsername(config.getUsername());
        ds.setPassword(config.getPassword());
        ds.setDriverClassName(config.getDriverClassName());

        MultiDataSourceProperties.HikariConfig hikari = config.getHikari();
        if (hikari != null) {
            ds.setMinimumIdle(hikari.getMinimumIdle());
            ds.setMaximumPoolSize(hikari.getMaximumPoolSize());
            ds.setIdleTimeout(hikari.getIdleTimeout());
            ds.setMaxLifetime(hikari.getMaxLifetime());
            ds.setConnectionTimeout(hikari.getConnectionTimeout());
            ds.setConnectionTestQuery(hikari.getConnectionTestQuery());
        }
        return ds;
    }
}
