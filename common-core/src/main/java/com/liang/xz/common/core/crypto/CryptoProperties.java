package com.liang.xz.common.core.crypto;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 自定义加密配置属性
 */
@Data
@ConfigurationProperties(prefix = "crypto.config")
public class CryptoProperties {

    /** 加密算法: AES / SM4 / DES */
    private String algorithm = "AES";

    /** 主密钥 */
    private String masterKey = "a1b2c3d4e5f6g7h8";
}
