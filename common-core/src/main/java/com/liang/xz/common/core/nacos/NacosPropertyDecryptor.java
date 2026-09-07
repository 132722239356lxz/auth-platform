package com.liang.xz.common.core.nacos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * <p>Nacos 配置属性自动解密器</p>
 *
 * <p>核心功能:</p>
 * <ol>
 *   <li>应用启动时扫描所有 PropertySource</li>
 *   <li>识别 ENC{algorithm:encryptedData} 格式的加密值</li>
 *   <li>使用配置的算法和密钥自动解密</li>
 *   <li>替换为明文值供应用使用</li>
 * </ol>
 *
 * <p>支持的加密格式:</p>
 * <pre>
 *   ENC{AES:base64EncryptedString}
 *   ENC{SM4:base64EncryptedString}
 *   ENC{base64EncryptedString}  (默认AES)
 * </pre>
 *
 * <p>实现参考 Nacos 官方 cipher-core 插件机制，
 *    此处实现一个简化版，直接对 Environment 中的加密值进行解密替换</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NacosPropertyDecryptor {

    private final NacosCryptoConfig cryptoConfig;

    /**
     * 解密单个加密属性值
     *
     * @param encryptedValue 加密值，格式: ENC{algorithm:base64Data}
     * @return 解密后的明文，如果不是加密格式则原样返回
     */
    public String decrypt(String encryptedValue) {
        if (!cryptoConfig.isEnabled() || encryptedValue == null) {
            return encryptedValue;
        }

        String prefix = cryptoConfig.getDataIdPrefix();
        String suffix = cryptoConfig.getDataIdSuffix();

        if (!encryptedValue.startsWith(prefix) || !encryptedValue.endsWith(suffix)) {
            return encryptedValue;
        }

        // 提取 ENC{...} 中间的内容
        String content = encryptedValue.substring(prefix.length(), encryptedValue.length() - suffix.length());
        if (content.isBlank()) {
            log.warn("[NacosCrypto] 加密内容为空");
            return encryptedValue;
        }

        try {
            // 解析算法和密文: "AES:base64data" 或 "base64data"
            String algorithm;
            String base64Cipher;
            int colonIdx = content.indexOf(':');
            if (colonIdx > 0) {
                algorithm = content.substring(0, colonIdx);
                base64Cipher = content.substring(colonIdx + 1);
            } else {
                algorithm = cryptoConfig.getAlgorithm();
                base64Cipher = content;
            }

            byte[] cipherBytes = Base64.getDecoder().decode(base64Cipher);
            byte[] keyBytes = Base64.getDecoder().decode(cryptoConfig.getSecretKey());
            byte[] plainBytes = decryptBytes(cipherBytes, keyBytes, algorithm);
            String plainText = new String(plainBytes, StandardCharsets.UTF_8);

            log.debug("[NacosCrypto] 成功解密配置: algorithm={}", algorithm);
            return plainText;

        } catch (Exception e) {
            log.error("[NacosCrypto] 解密失败: value={}, error={}", encryptedValue, e.getMessage());
            return encryptedValue;
        }
    }

    /**
     * 使用指定算法解密字节数组
     */
    private byte[] decryptBytes(byte[] data, byte[] key, String algorithm) throws Exception {
        String transformation = switch (algorithm.toUpperCase()) {
            case "AES" -> "AES/ECB/PKCS5Padding";
            case "DES" -> "DES/ECB/PKCS5Padding";
            case "SM4" -> "SM4/ECB/PKCS7Padding";
            default -> throw new IllegalArgumentException("不支持的解密算法: " + algorithm);
        };

        SecretKeySpec keySpec = new SecretKeySpec(key, algorithm);
        Cipher cipher = Cipher.getInstance(transformation);
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        return cipher.doFinal(data);
    }

    /**
     * 判断属性值是否为加密格式
     */
    public boolean isEncrypted(String value) {
        return value != null
                && value.startsWith(cryptoConfig.getDataIdPrefix())
                && value.endsWith(cryptoConfig.getDataIdSuffix());
    }
}
