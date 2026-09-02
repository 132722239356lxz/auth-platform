package com.liang.xz.common.core.util;

import lombok.extern.slf4j.Slf4j;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * <p>RS256 (RSA-SHA256) 工具类 —— 非对称密钥签名与验证</p>
 *
 * <p>适用场景:</p>
 * <ul>
 *   <li><b>OAuth2 JWT 签名:</b> Spring Authorization Server 的 RS256 算法</li>
 *   <li><b>第三方客户端接入:</b> 使用公钥验证，私钥安全存储</li>
 *   <li><b>密钥轮换:</b> 支持生成多对密钥，实现平滑轮换</li>
 *   <li><b>数字签名:</b> 任意数据的非对称签名与验证</li>
 * </ul>
 *
 * <p>密钥格式:</p>
 * <ul>
 *   <li>私钥: PKCS#8 格式 (DER 编码 → Base64)</li>
 *   <li>公钥: X.509 格式 (DER 编码 → Base64)</li>
 * </ul>
 *
 * <p>使用示例:</p>
 * <pre>
 *   // 生成密钥对
 *   KeyPair keyPair = RS256Util.generateKeyPair();
 *
 *   // 导出私钥为 Base64 字符串（存配置）
 *   String privateKeyBase64 = RS256Util.exportPrivateKey(keyPair.getPrivate());
 *
 *   // 导出公钥为 Base64 字符串
 *   String publicKeyBase64 = RS256Util.exportPublicKey(keyPair.getPublic());
 *
 *   // 签名
 *   String sig = RS256Util.sign("payload", privateKeyBase64);
 *
 *   // 验签
 *   boolean ok = RS256Util.verify("payload", sig, publicKeyBase64);
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
public final class RS256Util {

    /** RSA 算法名称 */
    public static final String KEY_ALGORITHM = "RSA";

    /** SHA256withRSA 签名算法 */
    public static final String SIGN_ALGORITHM = "SHA256withRSA";

    /** 默认密钥长度 (2048 bits) */
    public static final int DEFAULT_KEY_SIZE = 2048;

    /** 高强度密钥长度 (4096 bits) */
    public static final int HIGH_KEY_SIZE = 4096;

    private RS256Util() {
        throw new UnsupportedOperationException("RS256Util is a utility class, do not instantiate");
    }

    // ==================== 密钥对生成 ====================

    /**
     * 生成 2048-bit RSA 密钥对
     *
     * @return RSA KeyPair
     */
    public static KeyPair generateKeyPair() {
        return generateKeyPair(DEFAULT_KEY_SIZE);
    }

    /**
     * 生成指定长度的 RSA 密钥对
     *
     * @param keySize 密钥长度 (2048 / 4096)
     * @return RSA KeyPair
     * @throws IllegalArgumentException 如果密钥长度不合法
     */
    public static KeyPair generateKeyPair(int keySize) {
        if (keySize < 2048) {
            throw new IllegalArgumentException("RSA 密钥长度至少需要 2048 bits, 当前: " + keySize);
        }
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            generator.initialize(keySize, new SecureRandom());
            KeyPair keyPair = generator.generateKeyPair();
            log.info("[RS256] 生成 RSA{} 密钥对成功", keySize);
            return keyPair;
        } catch (Exception e) {
            log.error("[RS256] 生成密钥对失败: {}", e.getMessage());
            throw new RuntimeException("RSA 密钥对生成失败", e);
        }
    }

    // ==================== 密钥导出 (Java对象 → Base64字符串) ====================

    /**
     * 导出 RSA 私钥为 Base64 字符串 (PKCS#8 格式)
     * <p>可直接存入 application.yml 的 oauth2.config.rsa-private-key</p>
     *
     * @param privateKey RSA 私钥
     * @return Base64 编码的私钥
     */
    public static String exportPrivateKey(PrivateKey privateKey) {
        if (privateKey == null) {
            throw new IllegalArgumentException("私钥不能为 null");
        }
        byte[] encoded = privateKey.getEncoded();
        return Base64.getEncoder().encodeToString(encoded);
    }

    /**
     * 导出 RSA 公钥为 Base64 字符串 (X.509 格式)
     *
     * @param publicKey RSA 公钥
     * @return Base64 编码的公钥
     */
    public static String exportPublicKey(PublicKey publicKey) {
        if (publicKey == null) {
            throw new IllegalArgumentException("公钥不能为 null");
        }
        byte[] encoded = publicKey.getEncoded();
        return Base64.getEncoder().encodeToString(encoded);
    }

    /**
     * 导出密钥对为配置友好的格式
     *
     * @param keyPair RSA 密钥对
     * @return {@link RsaKeyPair} 包含 Base64 格式的私钥和公钥
     */
    public static RsaKeyPair exportKeyPair(KeyPair keyPair) {
        return new RsaKeyPair(
                exportPrivateKey(keyPair.getPrivate()),
                exportPublicKey(keyPair.getPublic())
        );
    }

    /**
     * 一键生成并导出 RSA 密钥对 (最便捷的方式)
     *
     * @return {@link RsaKeyPair} 可用于配置文件
     */
    public static RsaKeyPair generateAndExportKeyPair() {
        KeyPair keyPair = generateKeyPair();
        return exportKeyPair(keyPair);
    }

    /**
     * 一键生成并导出 RSA 密钥对 (指定长度)
     *
     * @param keySize 密钥长度
     * @return {@link RsaKeyPair}
     */
    public static RsaKeyPair generateAndExportKeyPair(int keySize) {
        KeyPair keyPair = generateKeyPair(keySize);
        return exportKeyPair(keyPair);
    }

    // ==================== 密钥导入 (Base64字符串 → Java对象) ====================

    /**
     * 从 Base64 字符串导入 RSA 私钥 (PKCS#8)
     *
     * @param base64PrivateKey Base64 编码的私钥
     * @return RSAPrivateKey
     */
    public static RSAPrivateKey importPrivateKey(String base64PrivateKey) {
        if (base64PrivateKey == null || base64PrivateKey.isBlank()) {
            throw new IllegalArgumentException("私钥字符串不能为空");
        }
        try {
            // 移除所有空白字符，防止 PEM 格式换行导致 Base64 解码失败
            String cleanKey = base64PrivateKey.replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(cleanKey);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            return (RSAPrivateKey) keyFactory.generatePrivate(spec);
        } catch (Exception e) {
            log.error("[RS256] 导入私钥失败: {}", e.getMessage());
            throw new RuntimeException("RSA 私钥导入失败", e);
        }
    }

    /**
     * 从 Base64 字符串导入 RSA 公钥 (X.509)
     *
     * @param base64PublicKey Base64 编码的公钥
     * @return RSAPublicKey
     */
    public static RSAPublicKey importPublicKey(String base64PublicKey) {
        if (base64PublicKey == null || base64PublicKey.isBlank()) {
            throw new IllegalArgumentException("公钥字符串不能为空");
        }
        try {
            // 移除所有空白字符，防止 PEM 格式换行导致 Base64 解码失败
            String cleanKey = base64PublicKey.replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(cleanKey);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            return (RSAPublicKey) keyFactory.generatePublic(spec);
        } catch (Exception e) {
            log.error("[RS256] 导入公钥失败: {}", e.getMessage());
            throw new RuntimeException("RSA 公钥导入失败", e);
        }
    }

    /**
     * 从私钥推导公钥 (通过 modulus + publicExponent)
     *
     * @param base64PrivateKey Base64 编码的私钥
     * @return Base64 编码的公钥
     */
    public static String derivePublicKeyFromPrivateKey(String base64PrivateKey) {
        RSAPrivateKey privateKey = importPrivateKey(base64PrivateKey);
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            java.security.spec.RSAPublicKeySpec publicKeySpec = new java.security.spec.RSAPublicKeySpec(
                    privateKey.getModulus(),
                    java.math.BigInteger.valueOf(65537)
            );
            PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
            return exportPublicKey(publicKey);
        } catch (Exception e) {
            log.error("[RS256] 从私钥推导公钥失败: {}", e.getMessage());
            throw new RuntimeException("推导公钥失败", e);
        }
    }

    // ==================== 签名 ====================

    /**
     * 使用 RSA 私钥对数据进行 RS256 签名
     *
     * @param data 待签名数据
     * @param base64PrivateKey Base64 编码的私钥
     * @return Base64 编码的签名
     */
    public static String sign(String data, String base64PrivateKey) {
        RSAPrivateKey privateKey = importPrivateKey(base64PrivateKey);
        try {
            Signature signature = Signature.getInstance(SIGN_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] signed = signature.sign();
            return Base64.getEncoder().encodeToString(signed);
        } catch (Exception e) {
            log.error("[RS256] 签名失败: {}", e.getMessage());
            throw new RuntimeException("RS256 签名失败", e);
        }
    }

    /**
     * 使用 RSA 私钥对数据进行 RS256 签名（返回原始字节）
     */
    public static byte[] signToBytes(String data, String base64PrivateKey) {
        RSAPrivateKey privateKey = importPrivateKey(base64PrivateKey);
        try {
            Signature signature = Signature.getInstance(SIGN_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return signature.sign();
        } catch (Exception e) {
            log.error("[RS256] 签名失败: {}", e.getMessage());
            throw new RuntimeException("RS256 签名失败", e);
        }
    }

    // ==================== 验签 ====================

    /**
     * 使用 RSA 公钥验证 RS256 签名
     *
     * @param data 原始数据
     * @param base64Signature Base64 编码的签名
     * @param base64PublicKey Base64 编码的公钥
     * @return true 验签通过
     */
    public static boolean verify(String data, String base64Signature, String base64PublicKey) {
        try {
            RSAPublicKey publicKey = importPublicKey(base64PublicKey);
            Signature signature = Signature.getInstance(SIGN_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(base64Signature));
        } catch (Exception e) {
            log.warn("[RS256] 验签失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 使用 RSA 公钥验证 RS256 签名（原始字节格式）
     *
     * @param data 原始数据
     * @param sigBytes 签名字节
     * @param base64PublicKey Base64 编码的公钥
     * @return true 验签通过
     */
    public static boolean verifyBytes(String data, byte[] sigBytes, String base64PublicKey) {
        try {
            RSAPublicKey publicKey = importPublicKey(base64PublicKey);
            Signature signature = Signature.getInstance(SIGN_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return signature.verify(sigBytes);
        } catch (Exception e) {
            log.warn("[RS256] 验签失败: {}", e.getMessage());
            return false;
        }
    }

    // ==================== JWT 签名 ====================

    /**
     * 对 JWT header.payload 进行 RS256 签名，返回完整 Token
     *
     * @param headerPayload "header.payload" 格式
     * @param base64PrivateKey Base64 编码的私钥
     * @return 完整 JWT Token (header.payload.signature)
     */
    public static String signJwt(String headerPayload, String base64PrivateKey) {
        String signature = sign(headerPayload, base64PrivateKey);
        return headerPayload + "." + signature;
    }

    /**
     * 验证 RS256 JWT Token
     *
     * @param jwt 完整 JWT Token
     * @param base64PublicKey Base64 编码的公钥
     * @return true 验签通过
     */
    public static boolean verifyJwt(String jwt, String base64PublicKey) {
        if (jwt == null || jwt.isBlank()) {
            return false;
        }
        int lastDot = jwt.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == jwt.length() - 1) {
            return false;
        }
        String headerPayload = jwt.substring(0, lastDot);
        String signature = jwt.substring(lastDot + 1);
        return verify(headerPayload, signature, base64PublicKey);
    }

    // ==================== 工具方法 ====================

    /**
     * 获取 RSA 密钥对的详细信息 (调试用)
     *
     * @param base64PrivateKey Base64 编码的私钥
     * @return 密钥信息描述
     */
    public static String keyInfo(String base64PrivateKey) {
        try {
            RSAPrivateKey privateKey = importPrivateKey(base64PrivateKey);
            return String.format("RSA 私钥: %d bits, 算法=%s, 格式=%s",
                    privateKey.getModulus().bitLength(),
                    privateKey.getAlgorithm(),
                    privateKey.getFormat());
        } catch (Exception e) {
            return "RSA 密钥: 无效的 Base64 编码";
        }
    }

    /**
     * 验证私钥 Base64 字符串是否合法
     *
     * @param base64PrivateKey Base64 编码的私钥
     * @return true 合法
     */
    public static boolean isValidPrivateKey(String base64PrivateKey) {
        try {
            importPrivateKey(base64PrivateKey);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 验证公钥 Base64 字符串是否合法
     *
     * @param base64PublicKey Base64 编码的公钥
     * @return true 合法
     */
    public static boolean isValidPublicKey(String base64PublicKey) {
        try {
            importPublicKey(base64PublicKey);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 数据类 ====================

    /**
     * RSA 密钥对导出结果
     */
    public record RsaKeyPair(
            /** Base64 编码的私钥 (PKCS#8) */
            String privateKey,
            /** Base64 编码的公钥 (X.509) */
            String publicKey
    ) {

        /**
         * 格式化输出到控制台，方便复制到配置文件
         */
        @Override
        public String toString() {
            return "\n========== RSA 密钥对 ==========\n"
                    + "oauth2.config.rsa-private-key=" + privateKey + "\n\n"
                    + "oauth2.config.rsa-public-key=" + publicKey + "\n"
                    + "================================\n";
        }

        /**
         * 掩码输出（仅显示前后8位，中间用*代替，用于日志）
         */
        public String toMaskedString() {
            return "RsaKeyPair[privateKey=" + mask(privateKey) + ", publicKey=" + mask(publicKey) + "]";
        }

        private static String mask(String key) {
            if (key == null || key.length() <= 16) {
                return key;
            }
            return key.substring(0, 8) + "****" + key.substring(key.length() - 8);
        }
    }
}
