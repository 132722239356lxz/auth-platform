package com.liang.xz.common.core.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.regex.Pattern;

/**
 * <p>BCrypt 密码加密工具类</p>
 *
 * <p>核心能力:</p>
 * <ul>
 *   <li>密码加密：将明文密码 BCrypt 哈希后存入数据库</li>
 *   <li>密码校验：登录时对比明文输入与数据库中的哈希值</li>
 *   <li>强度自定义：支持指定 BCrypt rounds（4~31，默认10）</li>
 * </ul>
 *
 * <p>使用示例:</p>
 * <pre>
 *   // 注册时加密
 *   String hash = BCryptUtil.encode("admin123");
 *   user.setPassword(hash);
 *
 *   // 登录时校验
 *   boolean ok = BCryptUtil.matches("admin123", user.getPassword());
 * </pre>
 *
 * <p>安全说明:</p>
 * <ul>
 *   <li>BCrypt 自动内嵌随机盐，无需手动管理</li>
 *   <li>单次 encode 约 100ms（rounds=10），建议在前端做二次哈希后再传给后端</li>
 *   <li>哈希结果长度固定为 60 字符</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public final class BCryptUtil {

    private static final int DEFAULT_ROUNDS = 10;

    /** BCrypt hash 格式正则: $2a$|$2b$|$2y$ */
    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$.{53}$");

    private BCryptUtil() {
        // 工具类禁止实例化
    }

    // ==================== 默认强度(rounds=10) ====================

    /**
     * 加密明文密码（默认强度10）
     *
     * @param rawPassword 明文密码
     * @return BCrypt 哈希值（60字符）
     */
    public static String encode(String rawPassword) {
        return encode(rawPassword, DEFAULT_ROUNDS);
    }

    /**
     * 校验明文密码与 BCrypt 哈希是否匹配
     *
     * @param rawPassword     明文密码
     * @param encodedPassword BCrypt 哈希值
     * @return true=匹配, false=不匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        BCryptPasswordEncoder encoder = bcryptForHash(encodedPassword);
        return encoder.matches(rawPassword, encodedPassword);
    }

    // ==================== 自定义强度 ====================

    /**
     * 加密密码（自定义 BCrypt rounds）
     *
     * @param rawPassword 明文
     * @param rounds      强度 4~31，越高越安全但越慢
     * @return BCrypt 哈希
     */
    public static String encode(String rawPassword, int rounds) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (rounds < 4 || rounds > 31) {
            throw new IllegalArgumentException("BCrypt rounds 必须在 4~31 之间，当前: " + rounds);
        }
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(rounds);
        return encoder.encode(rawPassword);
    }

    // ==================== 升级检测 ====================

    /**
     * 判断是否需要升级哈希强度
     *
     * @param encodedPassword 当前存储的 BCrypt 哈希
     * @param targetRounds    目标 rounds
     * @return true=需要升级
     */
    public static boolean needsUpgrade(String encodedPassword, int targetRounds) {
        if (encodedPassword == null || !isBCryptHash(encodedPassword)) {
            return true;
        }
        try {
            int currentRounds = Integer.parseInt(encodedPassword.substring(4, 6));
            return currentRounds < targetRounds;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /**
     * 判断字符串是否是 BCrypt 哈希格式
     */
    public static boolean isBCryptHash(String value) {
        return value != null && BCRYPT_PATTERN.matcher(value).matches();
    }

    /**
     * 从已有的 BCrypt 哈希中提取 rounds 参数，创建匹配的编码器
     */
    private static BCryptPasswordEncoder bcryptForHash(String encodedPassword) {
        if (isBCryptHash(encodedPassword)) {
            int rounds = Integer.parseInt(encodedPassword.substring(4, 6));
            return new BCryptPasswordEncoder(rounds);
        }
        return new BCryptPasswordEncoder(DEFAULT_ROUNDS);
    }

    // ==================== 便捷方法 ====================

    /**
     * 生成随机密码（用于初始化或重置）
     *
     * @param length 密码长度(8~64)
     * @return 随机密码字符串，包含大小写字母+数字+特殊字符
     */
    public static String generateRandomPassword(int length) {
        if (length < 8 || length > 64) {
            throw new IllegalArgumentException("密码长度必须在 8~64 之间");
        }
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghjkmnpqrstuvwxyz";
        String digits = "23456789";
        String special = "!@#$%&*_";
        String all = upper + lower + digits + special;

        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        // 确保每类字符至少出现一次
        sb.append(upper.charAt(random.nextInt(upper.length())));
        sb.append(lower.charAt(random.nextInt(lower.length())));
        sb.append(digits.charAt(random.nextInt(digits.length())));
        sb.append(special.charAt(random.nextInt(special.length())));
        for (int i = 4; i < length; i++) {
            sb.append(all.charAt(random.nextInt(all.length())));
        }
        // 打乱顺序
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    /**
     * 命令行工具入口。
     *
     * <p>用法:</p>
     * <ul>
     *   <li>{@code java BCryptUtil} —— 生成 16 位随机强密码</li>
     *   <li>{@code echo '明文' | java BCryptUtil -} —— 从标准输入读取明文并输出哈希
     *       <b>（推荐，可避免密钥进入 shell 命令历史）</b></li>
     *   <li>{@code java BCryptUtil 明文} —— 直接对参数明文取哈希（会留存于命令历史，仅调试用）</li>
     * </ul>
     *
     * <p>典型用途：凭证轮换时为新的 client_secret 生成 {@code {bcrypt}} 哈希写入数据库。</p>
     *
     * @param args 命令行参数
     * @throws java.io.IOException 读取标准输入失败时抛出
     */
    public static void main(String[] args) throws java.io.IOException {
        if (args.length >= 1) {
            String plain;
            if ("-".equals(args[0])) {
                // 从标准输入读取，避免明文出现在 shell 历史中
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8))) {
                    plain = reader.readLine();
                }
            } else {
                plain = args[0];
            }
            if (plain != null && !plain.isBlank()) {
                System.out.println(encode(plain.trim()));
            }
            return;
        }
        System.out.println(generateRandomPassword(16));
    }
}
