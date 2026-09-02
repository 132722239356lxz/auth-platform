package com.liang.xz.common.core.datasource;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>多数据源配置属性</p>
 *
 * <p>YAML 配置示例:</p>
 * <pre>
 * spring:
 *   datasource:
 *     primary:
 *       url: jdbc:mysql://localhost:3306/auth_platform
 *       username: root
 *       password: xxx
 *       driver-class-name: com.mysql.cj.jdbc.Driver
 *     secondary:
 *       - name: log-db
 *         url: jdbc:mysql://localhost:3306/auth_log
 *         username: root
 *         password: xxx
 *         driver-class-name: com.mysql.cj.jdbc.Driver
 *       - name: biz-db
 *         url: jdbc:mysql://localhost:3306/auth_biz
 *         username: root
 *         password: xxx
 *         driver-class-name: com.mysql.cj.jdbc.Driver
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "spring.datasource")
public class MultiDataSourceProperties {

    /**
     * 主数据源配置（必需）
     */
    private DataSourceConfig primary;

    /**
     * 从数据源列表（可选，按 name 区分）
     */
    private Map<String, DataSourceConfig> secondary = new HashMap<>();

    /**
     * 单个数据源配置
     */
    @Data
    public static class DataSourceConfig {
        /** 数据源名称（secondary 时必填） */
        private String name;
        /** JDBC URL */
        private String url;
        /** 用户名 */
        private String username;
        /** 密码 */
        private String password;
        /** 驱动类名 */
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
        /** 连接池类型: hikari(默认) | druid */
        private String poolType = "hikari";
        /** Hikari 连接池配置 */
        private HikariConfig hikari = new HikariConfig();
    }

    @Data
    public static class HikariConfig {
        private int minimumIdle = 5;
        private int maximumPoolSize = 20;
        private long idleTimeout = 300000;
        private long maxLifetime = 1200000;
        private long connectionTimeout = 30000;
        private String connectionTestQuery = "SELECT 1";
    }
}
