package com.liang.xz.common.core.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * <p>配置文件加密/解密工具类</p>
 *
 * <p>核心功能:</p>
 * <ol>
 *   <li>AES/CBC/PKCS5Padding 加解密（推荐，带 IV）</li>
 *   <li>AES/ECB/PKCS5Padding 加解密（兼容 NacosCryptoConfig）</li>
 *   <li>自动生成加密密钥</li>
 *   <li>加解密单行配置值</li>
 *   <li>批量为配置文件加密敏感项</li>
 * </ol>
 *
 * <p>加密格式:</p>
 * <pre>
 *   原始值:   root
 *   加密后:   ENC{AES:base64CipherText}
 *   注解:     与 NacosPropertyDecryptor 的解密格式保持一致
 * </pre>
 *
 * <p>使用场景:</p>
 * <ul>
 *   <li>加密 application.yml 中的密码、密钥、Token 等敏感配置</li>
 *   <li>加密 Nacos 配置中心中的敏感项</li>
 *   <li>生成数据源密码密文</li>
 * </ul>
 *
 * <p>使用示例:</p>
 * <pre>
 *   // 生成密钥
 *   String secretKey = ConfigCryptoUtil.generateKey();
 *
 *   // 加密
 *   String encrypted = ConfigCryptoUtil.encrypt("root", secretKey);
 *   // → ENC{AES:xxxBase64xxx}
 *
 *   // 解密
 *   String plain = ConfigCryptoUtil.decrypt(encrypted, secretKey);
 *   // → root
 *
 *   // 使用明文解密(如果不是加密格式则原样返回)
 *   String result = ConfigCryptoUtil.decryptIfEncrypted("root", secretKey);
 *   // → root (明文直接返回)
 *
 *   String result2 = ConfigCryptoUtil.decryptIfEncrypted("ENC{AES:xxx}", secretKey);
 *   // → root (密文解密)
 *
 *   // 批量为 yaml 配置加密
 *   String yamlContent = ConfigCryptoUtil.encryptYamlValue(
 *       "spring.datasource.password: root", "password", secretKey);
 *   // → spring.datasource.password: ENC{AES:xxx}
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
public final class ConfigCryptoUtil {

    /** 默认加密算法 */
    public static final String DEFAULT_ALGORITHM = "AES";

    /** AES 密钥长度 (bits) */
    public static final int AES_KEY_SIZE = 256;

    /** 加密前缀标识 */
    public static final String ENC_PREFIX = "ENC{";

    /** 加密后缀标识 */
    public static final String ENC_SUFFIX = "}";

    /** IV 长度 (bytes, AES CBC 模式) */
    private static final int IV_LENGTH = 16;

    // ==================== 构造 ====================

    private ConfigCryptoUtil() {
        throw new UnsupportedOperationException("ConfigCryptoUtil is a utility class, do not instantiate");
    }

    // ==================== 密钥生成 ====================

    /**
     * 生成 AES-256 加密密钥 (Base64 编码)
     * <p>可直接配置到 nacos.crypto.secret-key</p>
     *
     * @return Base64 编码的 AES 密钥
     */
    public static String generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(DEFAULT_ALGORITHM);
            keyGenerator.init(AES_KEY_SIZE, new SecureRandom());
            SecretKey secretKey = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            log.error("[ConfigCrypto] 生成密钥失败: {}", e.getMessage());
            throw new RuntimeException("AES 密钥生成失败", e);
        }
    }

    /**
     * 从 Base64 字符串还原 AES 密钥对象
     *
     * @param base64Key Base64 编码的密钥
     * @return SecretKeySpec
     */
    public static SecretKeySpec parseKey(String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        return new SecretKeySpec(keyBytes, DEFAULT_ALGORITHM);
    }

    // ==================== 加密 (CBC 推荐) ====================

    /**
     * 使用 AES/CBC/PKCS5Padding 加密，返回完整加密格式
     * <p>格式: ENC{AES:base64(iv + ciphertext)}</p>
     *
     * @param plainText 明文
     * @param base64Key Base64 编码的 AES 密钥
     * @return 加密后的配置格式字符串
     */
    public static String encrypt(String plainText, String base64Key) {
        return encryptWithAlgorithm(plainText, base64Key, DEFAULT_ALGORITHM);
    }

    /**
     * 使用 AES/CBC/PKCS5Padding 加密，只返回密文（不带 ENC 前缀）
     *
     * @param plainText 明文
     * @param base64Key Base64 编码的 AES 密钥
     * @return Base64 编码的密文 (iv + cipher text)
     */
    public static String encryptRaw(String plainText, String base64Key) {
        try {
            SecretKeySpec keySpec = parseKey(base64Key);

            // 生成随机 IV
            byte[] iv = new byte[IV_LENGTH];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // 加密
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // 拼接 IV + 密文
            byte[] combined = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, IV_LENGTH, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("[ConfigCrypto] 加密失败: {}", e.getMessage());
            throw new RuntimeException("AES 加密失败", e);
        }
    }

    /**
     * 使用指定算法加密，返回 ENC{算法:base64} 格式
     */
    private static String encryptWithAlgorithm(String plainText, String base64Key, String algorithm) {
        String cipherText = encryptRaw(plainText, base64Key);
        return ENC_PREFIX + algorithm + ":" + cipherText + ENC_SUFFIX;
    }

    // ==================== 解密 ====================

    /**
     * 解密加密格式的配置值 (CBC 模式)
     * <p>识别 ENC{Algorithm:base64} 格式并解密</p>
     *
     * @param encryptedValue 加密配置值，如 ENC{AES:xxx}
     * @param base64Key Base64 编码的 AES 密钥
     * @return 解密后的明文
     */
    public static String decrypt(String encryptedValue, String base64Key) {
        if (!isEncrypted(encryptedValue)) {
            log.debug("[ConfigCrypto] 非加密格式，原样返回");
            return encryptedValue;
        }

        // 提取算法和密文
        String content = encryptedValue.substring(ENC_PREFIX.length(),
                encryptedValue.length() - ENC_SUFFIX.length());
        int colonIdx = content.indexOf(':');
        String algorithm = colonIdx > 0 ? content.substring(0, colonIdx) : DEFAULT_ALGORITHM;
        String cipherText = colonIdx > 0 ? content.substring(colonIdx + 1) : content;

        return decryptRaw(cipherText, base64Key);
    }

    /**
     * 解密 Base64 密文 (IV + cipher text)
     *
     * @param base64Cipher Base64 编码的密文
     * @param base64Key Base64 编码的 AES 密钥
     * @return 明文
     */
    public static String decryptRaw(String base64Cipher, String base64Key) {
        try {
            SecretKeySpec keySpec = parseKey(base64Key);
            byte[] combined = Base64.getDecoder().decode(base64Cipher);

            // 提取 IV
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

            // 提取密文
            byte[] cipherBytes = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, cipherBytes, 0, cipherBytes.length);

            // 解密
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
            byte[] plainBytes = cipher.doFinal(cipherBytes);

            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[ConfigCrypto] 解密失败: {}", e.getMessage());
            throw new RuntimeException("AES 解密失败", e);
        }
    }

    /**
     * 安全解密：如果是加密格式则解密，否则原样返回
     *
     * @param value 可能是加密或明文的配置值
     * @param base64Key Base64 编码的密钥
     * @return 明文
     */
    public static String decryptIfEncrypted(String value, String base64Key) {
        if (!isEncrypted(value)) {
            return value;
        }
        try {
            return decrypt(value, base64Key);
        } catch (Exception e) {
            log.warn("[ConfigCrypto] 解密失败，返回原值: {}", e.getMessage());
            return value;
        }
    }

    // ==================== ECB 模式（兼容 NacosCryptoConfig） ====================

    /**
     * 使用 AES/ECB/PKCS5Padding 加密（兼容 NacosPropertyDecryptor）
     *
     * @param plainText 明文
     * @param base64Key Base64 编码的密钥
     * @return ENC{AES:base64CipherText}
     */
    public static String encryptEcb(String plainText, String base64Key) {
        try {
            SecretKeySpec keySpec = parseKey(base64Key);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            String cipherText = Base64.getEncoder().encodeToString(encrypted);
            return ENC_PREFIX + "AES:" + cipherText + ENC_SUFFIX;
        } catch (Exception e) {
            log.error("[ConfigCrypto] ECB加密失败: {}", e.getMessage());
            throw new RuntimeException("AES/ECB 加密失败", e);
        }
    }

    /**
     * 使用 AES/ECB/PKCS5Padding 解密（兼容 NacosPropertyDecryptor）
     *
     * @param encryptedValue ENC{AES:base64CipherText}
     * @param base64Key Base64 编码的密钥
     * @return 明文
     */
    public static String decryptEcb(String encryptedValue, String base64Key) {
        if (!isEncrypted(encryptedValue)) {
            return encryptedValue;
        }

        String content = encryptedValue.substring(ENC_PREFIX.length(),
                encryptedValue.length() - ENC_SUFFIX.length());
        int colonIdx = content.indexOf(':');
        String cipherText = colonIdx > 0 ? content.substring(colonIdx + 1) : content;

        try {
            SecretKeySpec keySpec = parseKey(base64Key);
            byte[] cipherBytes = Base64.getDecoder().decode(cipherText);

            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] plainBytes = cipher.doFinal(cipherBytes);

            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[ConfigCrypto] ECB解密失败: {}", e.getMessage());
            throw new RuntimeException("AES/ECB 解密失败", e);
        }
    }

    // ==================== 批量处理 ====================

    /**
     * 加密 YAML/Properties 格式配置中的敏感字段
     * <p>输入一行配置，匹配敏感 key 时加密其 value</p>
     *
     * @param line 配置行，如 "spring.datasource.password: root"
     * @param sensitiveKey 敏感字段关键字，如 "password"
     * @param base64Key Base64 编码的密钥
     * @return 加密后的配置行
     */
    public static String encryptYamlValue(String line, String sensitiveKey, String base64Key) {
        if (line == null || line.isBlank()) {
            return line;
        }
        // 检查是否包含敏感key
        if (!line.toLowerCase().contains(sensitiveKey.toLowerCase())) {
            return line;
        }
        // 提取 key : value
        int colonIdx = line.indexOf(':');
        if (colonIdx < 0) {
            return line;
        }
        String key = line.substring(0, colonIdx).trim();
        String value = line.substring(colonIdx + 1).trim();

        // 如果已经是加密格式则跳过
        if (isEncrypted(value)) {
            return line;
        }

        // 跳过空值和占位符
        if (value.isEmpty() || value.startsWith("${")) {
            return line;
        }

        String encrypted = encrypt(value, base64Key);
        return key + ": " + encrypted;
    }

    // ==================== 格式判断 ====================

    /**
     * 判断配置值是否为加密格式
     *
     * @param value 配置值
     * @return true 是加密格式
     */
    public static boolean isEncrypted(String value) {
        return value != null
                && value.startsWith(ENC_PREFIX)
                && value.endsWith(ENC_SUFFIX);
    }

    /**
     * 去除加密格式包装，提取内容部分
     *
     * @param encryptedValue ENC{AES:xxx}
     * @return 内容部分，如 AES:xxx；如果不是加密格式则返回原值
     */
    public static String unwrap(String encryptedValue) {
        if (!isEncrypted(encryptedValue)) {
            return encryptedValue;
        }
        return encryptedValue.substring(ENC_PREFIX.length(),
                encryptedValue.length() - ENC_SUFFIX.length());
    }

    // ==================== 快速测试/演示方法 ====================

    /**
     * 演示方法：生成密钥 → 加密 → 解密 → 输出对比
     * <p>用于验证工具类工作正常</p>
     *
     * @param sampleText 示例明文
     * @return 演示结果字符串
     */
    public static String demo(String sampleText) {
        String key = generateKey();
        String encrypted = encrypt(sampleText, key);
        String decrypted = decrypt(encrypted, key);
        boolean ok = sampleText.equals(decrypted);

        return String.format("""
                        ╔══════════════════════════════════════════╗
                        ║         ConfigCryptoUtil 演示           ║
                        ╠══════════════════════════════════════════╣
                        ║ 密钥:   %s ║
                        ║ 明文:   %s ║
                        ║ 密文:   %s ║
                        ║ 解密:   %s ║
                        ║ 结果:   %s                                         ║
                        ╚══════════════════════════════════════════╝""",
                key,
                sampleText,
                encrypted,
                decrypted,
                ok ? "✓ 成功" : "✗ 失败");
    }

    /**
     * 命令行入口：生成密钥 或 加密/解密
     * <pre>
     *   # 生成密钥
     *   java ConfigCryptoUtil genkey
     *
     *   # 加密
     *   java ConfigCryptoUtil encrypt "明文" "密钥"
     *
     *   # 解密
     *   java ConfigCryptoUtil decrypt "ENC{AES:xxx}" "密钥"
     *
     *   # 演示
     *   java ConfigCryptoUtil demo "密码文本"
     * </pre>
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("ConfigCryptoUtil 用法:");
            System.out.println("  genkey              - 生成 AES-256 密钥");
            System.out.println("  encrypt <明文字符串> <Base64密钥>  - 加密");
            System.out.println("  decrypt <ENC{...}> <Base64密钥>    - 解密");
            System.out.println("  demo <文本>         - 完整演示");
            return;
        }

        switch (args[0].toLowerCase()) {
            case "genkey" -> {
                String key = generateKey();
                System.out.println("nacos.crypto.secret-key=" + key);
            }
            case "encrypt" -> {
                if (args.length < 3) {
                    System.out.println("用法: encrypt <明文字符串> <Base64密钥>");
                    return;
                }
                String enc = encrypt(args[1], args[2]);
                System.out.println(enc);
            }
            case "decrypt" -> {
                if (args.length < 3) {
                    System.out.println("用法: decrypt <ENC{...}> <Base64密钥>");
                    return;
                }
                String dec = decrypt(args[1], args[2]);
                System.out.println(dec);
            }
            case "demo" -> {
                String text = args.length > 1 ? args[1] : "MySecretPassword123!";
                System.out.println(demo(text));
            }
            default -> System.out.println("未知命令: " + args[0]);
        }
    }
}
