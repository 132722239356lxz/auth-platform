package com.liang.xz.gateway.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>网关公开API配置 —— 配合 {@code @PublicApi} 注解使用</p>
 *
 * <p>业务模块使用了 {@code @PublicApi} 注解的接口，需要在网关层配置对应路径才能放行。
 * 推荐将配置放在 Nacos 共享配置中，支持动态刷新。</p>
 *
 * <p>Nacos 配置示例 (gateway-public-apis.yml):</p>
 * <pre>
 *   auth:
 *     gateway:
 *       public-api-paths:
 *         - /ai-agent-server/api/callback/**
 *         - /system-server/api/public/**
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "auth.gateway")
public class GatewayPublicApiProperties {

    /**
     * 公开API路径列表，网关层跳过认证直接放行
     * <p>支持 Ant 风格路径匹配，如 /api/public/**、/callback/wechat/*</p>
     */
    private List<String> publicApiPaths = new ArrayList<>();
}
