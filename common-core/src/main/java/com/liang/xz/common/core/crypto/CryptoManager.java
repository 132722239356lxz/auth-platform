package com.liang.xz.common.core.crypto;

import com.liang.xz.common.core.crypto.strategy.CryptoStrategy;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 加解密管理器
 */
@Component
public class CryptoManager {

    private final CryptoProperties cryptoProperties;
    private final Map<CryptoAlgorithm, CryptoStrategy> strategyMap;

    public CryptoManager(CryptoProperties cryptoProperties, List<CryptoStrategy> strategies) {
        this.cryptoProperties = cryptoProperties;
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(CryptoStrategy::supportedAlgorithm, Function.identity()));
    }

    public CryptoStrategy getStrategy() {
        CryptoAlgorithm algo = CryptoAlgorithm.fromName(cryptoProperties.getAlgorithm());
        CryptoStrategy strategy = strategyMap.get(algo);
        if (strategy == null) {
            throw new CryptoException("未找到算法 [" + algo.getName() + "] 的加解密策略实现");
        }
        return strategy;
    }

    public String encrypt(String plainText) {
        byte[] key = getMasterKeyBytes();
        return getStrategy().encrypt(plainText, key);
    }

    private static final String NOOP_PREFIX = "{noop}";

    private static final String BCRYPT_PREFIX = "{bcrypt}";

    /**
     * 解密客户端密钥。
     *
     * <p>支持三种存储格式:</p>
     * <ul>
     *     <li>{@code {noop}xxx} —— 明文存储（开发/测试用），直接返回 {@code xxx}</li>
     *     <li>{@code {bcrypt}\$2a\$...} —— 已 BCrypt 哈希存储，直接返回哈希值</li>
     *     <li>其他 —— 视为 AES 密文，使用配置的算法解密</li>
     * </ul>
     */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        if (cipherText.startsWith(NOOP_PREFIX)) {
            return cipherText.substring(NOOP_PREFIX.length());
        }
        if (cipherText.startsWith(BCRYPT_PREFIX)) {
            return cipherText.substring(BCRYPT_PREFIX.length());
        }
        byte[] key = getMasterKeyBytes();
        return getStrategy().decrypt(cipherText, key);
    }

    public String encrypt(String plainText, CryptoAlgorithm algorithm, byte[] key) {
        CryptoStrategy strategy = strategyMap.get(algorithm);
        if (strategy == null) {
            throw new CryptoException("未找到算法 [" + algorithm.getName() + "] 的加解密策略实现");
        }
        return strategy.encrypt(plainText, key);
    }

    public String decrypt(String cipherText, CryptoAlgorithm algorithm, byte[] key) {
        CryptoStrategy strategy = strategyMap.get(algorithm);
        if (strategy == null) {
            throw new CryptoException("未找到算法 [" + algorithm.getName() + "] 的加解密策略实现");
        }
        return strategy.decrypt(cipherText, key);
    }

    public String getCurrentAlgorithm() {
        return cryptoProperties.getAlgorithm();
    }

    private byte[] getMasterKeyBytes() {
        byte[] key = cryptoProperties.getMasterKey().getBytes(StandardCharsets.UTF_8);
        int minLen = CryptoAlgorithm.fromName(cryptoProperties.getAlgorithm()).getMinKeyLength();
        if (key.length < minLen) {
            throw new CryptoException("密钥长度不足，算法 [" + cryptoProperties.getAlgorithm()
                    + "] 要求至少 " + minLen + " 字节");
        }
        if (key.length > minLen) {
            byte[] truncated = new byte[minLen];
            System.arraycopy(key, 0, truncated, 0, minLen);
            return truncated;
        }
        return key;
    }
}
