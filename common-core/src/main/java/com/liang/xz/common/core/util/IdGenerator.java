package com.liang.xz.common.core.util;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <p>ID 生成工具类</p>
 *
 * <p>提供:</p>
 * <ul>
 *   <li>{@link #uuid()} — 标准 UUID（36 字符，含连字符）</li>
 *   <li>{@link #uuid32()} — 去连字符 UUID（32 字符）</li>
 *   <li>{@link #uuidShort()} — 短 UUID（22 字符，URL 安全）</li>
 *   <li>{@link #traceId()} — TraceId（32 字符，含时间戳+随机数）</li>
 *   <li>{@link #randomCode(int)} — 随机数字验证码</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public final class IdGenerator {

    private static final char[] BASE62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private IdGenerator() {
    }

    /**
     * 标准 UUID: 550e8400-e29b-41d4-a716-446655440000
     */
    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * 去连字符 UUID: 550e8400e29b41d4a716446655440000
     */
    public static String uuid32() {
        return uuid().replace("-", "");
    }

    /**
     * 短 UUID（URL 安全，Base62 编码，22 字符）
     */
    public static String uuidShort() {
        UUID uuid = UUID.randomUUID();
        return base62Encode(uuid.getMostSignificantBits()) + base62Encode(uuid.getLeastSignificantBits());
    }

    /**
     * TraceId: 16 位时间戳 + 16 位随机数 = 32 字符
     */
    public static String traceId() {
        long timeComponent = System.currentTimeMillis();
        long randomComponent = ThreadLocalRandom.current().nextLong();
        return String.format("%016x%016x", timeComponent, randomComponent);
    }

    /**
     * 随机数字验证码
     *
     * @param length 长度 (4~8)
     */
    public static String randomCode(int length) {
        if (length < 4 || length > 8) {
            throw new IllegalArgumentException("验证码长度应在 4~8 之间");
        }
        StringBuilder sb = new StringBuilder(length);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * 生成指定长度的随机字符串（字母+数字）
     */
    public static String randomString(int length) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(BASE62_CHARS[random.nextInt(BASE62_CHARS.length)]);
        }
        return sb.toString();
    }

    // ==================== 内部方法 ====================

    private static String base62Encode(long value) {
        char[] buffer = new char[11];
        for (int i = 10; i >= 0; i--) {
            buffer[i] = BASE62_CHARS[(int) (value & 0x3FL)];
            value >>>= 6;
        }
        return new String(buffer);
    }
}
