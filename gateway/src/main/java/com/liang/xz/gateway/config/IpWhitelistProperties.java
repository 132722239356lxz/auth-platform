package com.liang.xz.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>IP白名单配置属性 —— 支持从配置文件加载IP白名单列表</p>
 *
 * <p>配置示例 (application.yml):</p>
 * <pre>
 * security:
 *   ip-whitelist:
 *     enabled: true
 *     ips:
 *       - 127.0.0.1
 *       - 192.168.1.0/24
 *       - 10.0.0.0/8
 * </pre>
 *
 * @author auth-platform
 * @since 1.1.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "security.ip-whitelist")
public class IpWhitelistProperties {

    /** 是否启用IP白名单 */
    private boolean enabled = true;

    /** IP白名单列表，支持单个IP和CIDR格式 */
    private List<String> ips = new ArrayList<>();

}
