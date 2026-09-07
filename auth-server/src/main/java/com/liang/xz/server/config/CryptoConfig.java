package com.liang.xz.server.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

/**
 * <p>RSA 密钥对配置 —— 用于前端密码加密传输</p>
 *
 * <p>流程:</p>
 * <ol>
 *   <li>服务启动时生成 2048 位 RSA 密钥对（仅内存中，不落盘）</li>
 *   <li>前端通过 /api/crypto/public-key 获取公钥（PEM 格式）</li>
 *   <li>前端使用 RSA-OAEP 公钥加密密码后传输</li>
 *   <li>后端使用私钥解密，再走 BCrypt 校验</li>
 * </ol>
 *
 * <p>安全说明:</p>
 * <ul>
 *   <li>密钥对只在 JVM 内存中，重启后重新生成</li>
 *   <li>每次服务重启意味着前端必须重新获取公钥（crypto.ts 中无缓存或按需刷新）</li>
 *   <li>符合生产环境密码加密传输的合规要求</li>
 * </ul>
 *
 * @author auth-platform
 * @since 2.0.0
 */
@Slf4j
@Getter
@Configuration
public class CryptoConfig {

    private static final int KEY_SIZE = 2048;
    private static final String ALGORITHM = "RSA";

    private PublicKey publicKey;
    private PrivateKey privateKey;

    @PostConstruct
    public void init() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
            generator.initialize(KEY_SIZE);
            KeyPair keyPair = generator.generateKeyPair();
            this.publicKey = keyPair.getPublic();
            this.privateKey = keyPair.getPrivate();
            log.info("[Crypto] RSA {} 位密钥对初始化完成", KEY_SIZE);
        } catch (Exception e) {
            log.error("[Crypto] RSA 密钥对初始化失败", e);
            throw new RuntimeException("RSA 密钥对初始化失败", e);
        }
    }

    /**
     * 获取 PEM 格式的公钥字符串，供前端使用
     */
    public String getPublicKeyPem() {
        byte[] encoded = publicKey.getEncoded();
        String base64 = Base64.getEncoder().encodeToString(encoded);
        return "-----BEGIN PUBLIC KEY-----\n"
                + base64.replaceAll("(.{64})", "$1\n")
                + "\n-----END PUBLIC KEY-----";
    }
}
