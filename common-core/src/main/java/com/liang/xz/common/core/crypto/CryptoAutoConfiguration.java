package com.liang.xz.common.core.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 加解密模块自动配置
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties({CryptoProperties.class})
public class CryptoAutoConfiguration {

    public CryptoAutoConfiguration() {
        log.info("[CryptoAutoConfiguration] 加解密模块已启用");
    }
}
