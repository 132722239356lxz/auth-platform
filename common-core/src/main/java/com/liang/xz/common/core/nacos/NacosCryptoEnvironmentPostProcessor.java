package com.liang.xz.common.core.nacos;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * <p>Nacos 配置加密 —— EnvironmentPostProcessor 实现</p>
 *
 * <p>执行时机: Spring Environment 加载完成后、Bean 初始化之前</p>
 *
 * <p>工作流程:</p>
 * <ol>
 *   <li>检查是否启用了 nacos.crypto.enabled=true</li>
 *   <li>遍历所有 PropertySource</li>
 *   <li>识别 ENC{...} 格式的加密值</li>
 *   <li>解密后替换为明文值</li>
 * </ol>
 *
 * <p>配置方式(在 bootstrap.yml 或 application.yml 中):</p>
 * <pre>
 *   nacos:
 *     crypto:
 *       enabled: true
 *       algorithm: AES
 *       secret-key: base64EncodedSecretKey
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
public class NacosCryptoEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** 该处理器优先级需在 Nacos Config 加载之后 */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 检查是否启用加密
        String enabled = environment.getProperty("nacos.crypto.enabled");
        if (!"true".equals(enabled)) {
            log.info("[NacosCrypto] 配置加密未启用，跳过");
            return;
        }

        String algorithm = environment.getProperty("nacos.crypto.algorithm", "AES");
        String secretKey = environment.getProperty("nacos.crypto.secret-key");
        String prefix = environment.getProperty("nacos.crypto.data-id-prefix", "ENC{");
        String suffix = environment.getProperty("nacos.crypto.data-id-suffix", "}");

        if (secretKey == null || secretKey.isEmpty()) {
            log.warn("[NacosCrypto] 未配置 secret-key，跳过解密");
            return;
        }

        log.info("[NacosCrypto] 开始扫描加密配置: algorithm={}", algorithm);

        try {
            byte[] keyBytes = Base64.getDecoder().decode(secretKey);
            int decryptedCount = 0;

            for (PropertySource<?> propertySource : environment.getPropertySources()) {
                if (propertySource instanceof OriginTrackedMapPropertySource source) {
                    Map<String, Object> decryptedMap = decryptProperties(
                            source.getSource(), keyBytes, algorithm, prefix, suffix);

                    if (!decryptedMap.isEmpty()) {
                        decryptedCount += decryptedMap.size();
                        // 创建新的 PropertySource 替换原值
                        Map<String, Object> mergedSource = new LinkedHashMap<>(source.getSource());
                        mergedSource.putAll(decryptedMap);
                        environment.getPropertySources().replace(
                                source.getName(),
                                new OriginTrackedMapPropertySource(source.getName(), mergedSource));
                    }
                }
            }

            log.info("[NacosCrypto] 解密完成，共处理 {} 个加密属性", decryptedCount);

        } catch (Exception e) {
            log.error("[NacosCrypto] 解密过程异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 遍历属性Map，解密所有ENC{...}格式的值
     */
    private Map<String, Object> decryptProperties(Map<String, Object> source,
                                                    byte[] keyBytes,
                                                    String algorithm,
                                                    String prefix,
                                                    String suffix) {
        Map<String, Object> decrypted = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String strValue
                    && strValue.startsWith(prefix)
                    && strValue.endsWith(suffix)) {
                String decryptedValue = decryptValue(strValue, keyBytes, algorithm, prefix, suffix);
                if (decryptedValue != null && !decryptedValue.equals(strValue)) {
                    decrypted.put(entry.getKey(), decryptedValue);
                    log.debug("[NacosCrypto] 解密属性: {} = ***", entry.getKey());
                }
            }
        }
        return decrypted;
    }

    /**
     * 解密单个 ENC{...} 格式的值
     */
    private String decryptValue(String encryptedValue, byte[] keyBytes,
                                 String algorithm, String prefix, String suffix) {
        try {
            String content = encryptedValue.substring(prefix.length(),
                    encryptedValue.length() - suffix.length());
            if (content.isEmpty()) return encryptedValue;

            // 解析算法:密文 或 密文
            String base64Cipher;
            String algo = algorithm;
            int colonIdx = content.indexOf(':');
            if (colonIdx > 0) {
                algo = content.substring(0, colonIdx);
                base64Cipher = content.substring(colonIdx + 1);
            } else {
                base64Cipher = content;
            }

            byte[] cipherBytes = Base64.getDecoder().decode(base64Cipher);

            String transformation = switch (algo.toUpperCase()) {
                case "AES" -> "AES/ECB/PKCS5Padding";
                case "DES" -> "DES/ECB/PKCS5Padding";
                default -> throw new IllegalArgumentException("不支持算法: " + algo);
            };

            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, algo);
            Cipher cipher = Cipher.getInstance(transformation);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] plainBytes = cipher.doFinal(cipherBytes);

            return new String(plainBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.warn("[NacosCrypto] 解密属性值失败: {}", e.getMessage());
            return encryptedValue;
        }
    }
}
