package com.liang.xz.common.core.nacos;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * <p>Nacos 配置加密属性 —— 与 Nacos 配置中心集成</p>
 *
 * <p>配置说明:</p>
 * <ul>
 *   <li>enabled: 是否启用配置加密(默认true)</li>
 *   <li>dataIdPrefix: 加密敏感配置的前缀标识(如 ENC{...})</li>
 *   <li>algorithm: 解密算法(默认AES)</li>
 *   <li>secretKey: 解密主密钥(需与Nacos配置中心的加密密钥一致)</li>
 * </ul>
 *
 * <p>使用方式:</p>
 * <pre>
 *   # Nacos 中的加密配置示例:
 *   spring.datasource.password=ENC{AES:base64EncryptedData}
 *   spring.datasource.username=ENC{AES:base64EncryptedData}
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "nacos.crypto")
public class NacosCryptoConfig {

    /** 是否启用Nacos配置加密解密 */
    private boolean enabled = true;

    /** 加密数据标识前缀，如 ENC{ */
    private String dataIdPrefix = "ENC{";

    /** 加密数据标识后缀，如 } */
    private String dataIdSuffix = "}";

    /** 解密算法: AES(默认) / SM4 */
    private String algorithm = "AES";

    /** 解密主密钥(Base64编码) */
    private String secretKey;
}
