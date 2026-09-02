package com.liang.xz.common.core.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * <p>HS256 (HMAC-SHA256) 工具类 —— 对称密钥签名与验证</p>
 *
 * <p>适用场景:</p>
 * <ul>
 *   <li><b>内部服务间调用:</b> 微服务间 JWT 签发与验证（共享密钥）</li>
 *   <li><b>API 网关签名:</b> 请求签名防篡改</li>
 *   <li><b>回调校验:</b> 第三方回调数据完整性验证</li>
 *   <li><b>配置签名:</b> 敏感配置完整性校验</li>
 * </ul>
 *
 * <p>密钥要求:</p>
 * <ul>
 *   <li>HS256 要求密钥长度 ≥ 256 bits (32 bytes)</li>
 *   <li>自动生成的密钥使用 SecureRandom，安全随机</li>
 *   <li>密钥可通过 Base64 编码存储和传输</li>
 * </ul>
 *
 * <p>使用示例:</p>
 * <pre>
 *   // 生成密钥
 *   String key = HS256Util.generateKey();
 *
 *   // 签名
 *   String signature = HS256Util.sign("payload-to-sign", key);
 *
 *   // 验签
 *   boolean valid = HS256Util.verify("payload-to-sign", signature, key);
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
public final class HS256Util {

    /** HMAC-SHA256 算法名称 */
    public static final String ALGORITHM = "HmacSHA256";

    /** 最小密钥长度 (bits) */
    public static final int MIN_KEY_BITS = 256;

    /** 最小密钥长度 (bytes) */
    public static final int MIN_KEY_BYTES = MIN_KEY_BITS / 8;

    private HS256Util() {
        throw new UnsupportedOperationException("HS256Util is a utility class, do not instantiate");
    }

    // ==================== 密钥生成 ====================

    /**
     * 生成 256-bit (32 bytes) HS256 随机密钥
     * <p>返回 Base64 编码的密钥字符串</p>
     *
     * @return Base64 编码的 HS256 密钥
     */
    public static String generateKey() {
        byte[] key = new byte[MIN_KEY_BYTES];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    /**
     * 生成指定长度的 HS256 随机密钥
     *
     * @param byteLength 密钥字节长度，必须 ≥ 32
     * @return Base64 编码的密钥字符串
     * @throws IllegalArgumentException 如果长度不满足要求
     */
    public static String generateKey(int byteLength) {
        if (byteLength < MIN_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "HS256 密钥长度至少需要 " + MIN_KEY_BYTES + " bytes, 当前: " + byteLength);
        }
        byte[] key = new byte[byteLength];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    /**
     * 从 Base64 编码字符串还原密钥字节数组
     *
     * @param base64Key Base64 编码的密钥
     * @return 密钥字节数组
     */
    public static byte[] decodeKey(String base64Key) {
        return Base64.getDecoder().decode(base64Key);
    }

    // ==================== 签名 ====================

    /**
     * 使用 HS256 算法对数据进行签名 (Base64 输出)
     *
     * @param data 待签名数据
     * @param base64Key Base64 编码的密钥
     * @return Base64 编码的签名结果
     * @throws IllegalArgumentException 如果密钥长度不足
     */
    public static String sign(String data, String base64Key) {
        byte[] keyBytes = decodeKey(base64Key);
        validateKeyLength(keyBytes);
        byte[] signature = sign(data.getBytes(StandardCharsets.UTF_8), keyBytes);
        return Base64.getEncoder().encodeToString(signature);
    }

    /**
     * 使用 HS256 算法对数据进行签名 (原始字节输出)
     *
     * @param data 待签名数据
     * @param base64Key Base64 编码的密钥
     * @return 原始签名字节
     */
    public static byte[] signToBytes(String data, String base64Key) {
        byte[] keyBytes = decodeKey(base64Key);
        validateKeyLength(keyBytes);
        return sign(data.getBytes(StandardCharsets.UTF_8), keyBytes);
    }

    /**
     * 使用 HS256 算法对字节数组签名
     */
    private static byte[] sign(byte[] data, byte[] key) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM);
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(keySpec);
            return mac.doFinal(data);
        } catch (Exception e) {
            log.error("[HS256] 签名失败: {}", e.getMessage());
            throw new RuntimeException("HS256 签名失败", e);
        }
    }

    // ==================== 验签 ====================

    /**
     * 验证 HS256 签名
     *
     * @param data 原始数据
     * @param base64Signature Base64 编码的签名
     * @param base64Key Base64 编码的密钥
     * @return true 验签通过 / false 验签失败
     */
    public static boolean verify(String data, String base64Signature, String base64Key) {
        try {
            byte[] keyBytes = decodeKey(base64Key);
            validateKeyLength(keyBytes);
            byte[] expectedSig = sign(data.getBytes(StandardCharsets.UTF_8), keyBytes);
            byte[] actualSig = Base64.getDecoder().decode(base64Signature);
            return MessageDigestUtil.constantTimeEquals(expectedSig, actualSig);
        } catch (Exception e) {
            log.warn("[HS256] 验签失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 验证 HS256 签名（原始字节格式）
     *
     * @param data 原始数据
     * @param actualSig 实际签名字节
     * @param base64Key Base64 编码的密钥
     * @return true 验签通过 / false 验签失败
     */
    public static boolean verifyBytes(String data, byte[] actualSig, String base64Key) {
        try {
            byte[] keyBytes = decodeKey(base64Key);
            validateKeyLength(keyBytes);
            byte[] expectedSig = sign(data.getBytes(StandardCharsets.UTF_8), keyBytes);
            return MessageDigestUtil.constantTimeEquals(expectedSig, actualSig);
        } catch (Exception e) {
            log.warn("[HS256] 验签失败: {}", e.getMessage());
            return false;
        }
    }

    // ==================== JWT 分割签名 ====================

    /**
     * 对 JWT header.payload 进行签名，返回完整的 HS256 JWT Token
     * <p>注意: 仅签名，不负责 header/payload 的 JSON 组装</p>
     *
     * @param headerPayload "header.payload" 格式的待签名串
     * @param base64Key Base64 编码的密钥
     * @return 完整 JWT token (header.payload.signature)
     */
    public static String signJwt(String headerPayload, String base64Key) {
        byte[] keyBytes = decodeKey(base64Key);
        validateKeyLength(keyBytes);
        String signature = sign(headerPayload, base64Key);
        return headerPayload + "." + signature;
    }

    /**
     * 验证 HS256 JWT Token
     *
     * @param jwt 完整 JWT Token (header.payload.signature)
     * @param base64Key Base64 编码的密钥
     * @return true 验签通过
     */
    public static boolean verifyJwt(String jwt, String base64Key) {
        if (jwt == null || jwt.isBlank()) {
            return false;
        }
        int lastDot = jwt.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == jwt.length() - 1) {
            return false;
        }
        String headerPayload = jwt.substring(0, lastDot);
        String signature = jwt.substring(lastDot + 1);
        return verify(headerPayload, signature, base64Key);
    }

    // ==================== 密钥校验 ====================

    /**
     * 校验密钥长度是否满足 HS256 要求
     *
     * @param base64Key Base64 编码的密钥
     * @return true 满足要求
     */
    public static boolean isValidKey(String base64Key) {
        try {
            byte[] keyBytes = decodeKey(base64Key);
            return keyBytes.length >= MIN_KEY_BYTES;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 校验并获取密钥长度信息
     *
     * @param base64Key Base64 编码的密钥
     * @return 密钥长度描述字符串
     */
    public static String keyInfo(String base64Key) {
        try {
            byte[] keyBytes = decodeKey(base64Key);
            return String.format("HS256密钥: %d bytes (%d bits), %s",
                    keyBytes.length, keyBytes.length * 8,
                    keyBytes.length >= MIN_KEY_BYTES ? "满足HS256要求" : "不满足HS256要求(需>=32 bytes)");
        } catch (Exception e) {
            return "HS256密钥: 无效的Base64编码";
        }
    }

    // ==================== 内部工具方法 ====================

    /**
     * 验证密钥长度
     */
    private static void validateKeyLength(byte[] keyBytes) {
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "HS256 密钥长度不足: 需要至少 " + MIN_KEY_BYTES + " bytes, 当前: " + keyBytes.length + " bytes");
        }
    }

    /**
     * 内部使用的常量时间比较工具
     */
    private static class MessageDigestUtil {
        /**
         * 常量时间字符串比较，防止时序攻击
         */
        static boolean constantTimeEquals(byte[] a, byte[] b) {
            if (a.length != b.length) {
                return false;
            }
            int result = 0;
            for (int i = 0; i < a.length; i++) {
                result |= a[i] ^ b[i];
            }
            return result == 0;
        }
    }
}
