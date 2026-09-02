package com.liang.xz;

import com.liang.xz.common.core.crypto.CryptoProperties;
import com.liang.xz.common.core.properties.OAuth2TokenKeyConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * <p>授权服务器启动类 —— 认证与授权分离架构的核心服务</p>
 *
 * <p>职责:</p>
 * <ul>
 *   <li>OAuth2 授权服务器: Token签发、客户端认证</li>
 *   <li>客户端管理: 第三方客户端注册、上下线、密钥管理</li>
 *   <li>授权审计: 授权记录查询、Token吊销、安全审计</li>
 *   <li>多租户支持: JWT中包含 tenant_id</li>
 *   <li>双算法签名: RS256 + HS256</li>
 * </ul>
 *
 * <p>注意: 业务服务不在此模块，业务服务使用 resource-server-starter 仅做资源认证</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = "com.liang.xz")
@EnableDiscoveryClient  // 注册到Nacos服务发现
@EnableConfigurationProperties({
        OAuth2TokenKeyConfig.class,
        CryptoProperties.class
})
public class AuthServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
