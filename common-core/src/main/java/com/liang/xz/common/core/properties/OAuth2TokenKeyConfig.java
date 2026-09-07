package com.liang.xz.common.core.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "oauth2.config")
public class OAuth2TokenKeyConfig {

    /** accessToken 有效期 秒 默认1h */
    private Long accessTokenTtl = 3600L;
    /** refreshToken 有效期 秒 默认12h */
    private Long refreshTokenTtl = 43200L;

    /** RSA私钥 用于RS256 */
    private String rsaPrivateKey;

    /** RSA公钥 用于RS256 */
    private String rsaPublicKey;
    /** HS256对称密钥 */
    private String hs256SecretKey;

    public Duration getAccessTokenTimeToLive() {
        return Duration.ofSeconds(accessTokenTtl);
    }

    public Duration getRefreshTokenTimeToLive() {
        return Duration.ofSeconds(refreshTokenTtl);
    }
}