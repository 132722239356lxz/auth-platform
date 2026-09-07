package com.liang.xz.common.core.util;

import java.util.regex.Pattern;

/**
 * <p>字符串处理工具类</p>
 *
 * <p>能力覆盖:</p>
 * <ul>
 *   <li>空值/空白判断</li>
 *   <li>脱敏处理（手机号、邮箱、身份证、Token）</li>
 *   <li>驼峰/下划线互转</li>
 *   <li>截断与省略</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public final class StringUtil {

    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");

    private StringUtil() {
    }

    // ==================== 空值判断 ====================

    public static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.isEmpty();
    }

    public static boolean isNotEmpty(CharSequence cs) {
        return !isEmpty(cs);
    }

    public static boolean isBlank(CharSequence cs) {
        if (cs == null) {
            return true;
        }
        int len = cs.length();
        for (int i = 0; i < len; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isNotBlank(CharSequence cs) {
        return !isBlank(cs);
    }

    // ==================== 脱敏处理 ====================

    /**
     * 手机号脱敏：138****5678
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 邮箱脱敏：a***@example.com
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int at = email.indexOf("@");
        String local = email.substring(0, at);
        String domain = email.substring(at);
        return (local.length() <= 2 ? local.charAt(0) + "***" : local.charAt(0) + "***" + local.charAt(local.length() - 1)) + domain;
    }

    /**
     * 身份证号脱敏：3201**********1234
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return idCard;
        }
        return idCard.substring(0, 4) + "**********" + idCard.substring(idCard.length() - 4);
    }

    /**
     * Token 脱敏：仅保留前8位+后4位
     */
    public static String maskToken(String token) {
        if (token == null || token.length() <= 20) {
            return "***";
        }
        return token.substring(0, 8) + "***" + token.substring(token.length() - 4);
    }

    /**
     * 通用脱敏：保留头部 headLen 和尾部 tailLen 个字符，中间用 mask 填充
     */
    public static String mask(String text, int headLen, int tailLen, String mask) {
        if (text == null || text.length() <= headLen + tailLen) {
            return mask;
        }
        return text.substring(0, headLen) + mask + text.substring(text.length() - tailLen);
    }

    // ==================== 命名转换 ====================

    /**
     * 驼峰 → 下划线: userName → user_name
     */
    public static String camelToUnderscore(String camel) {
        if (camel == null || camel.isEmpty()) {
            return camel;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char ch = camel.charAt(i);
            if (Character.isUpperCase(ch)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(ch));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * 下划线 → 驼峰: user_name → userName
     */
    public static String underscoreToCamel(String underscore) {
        if (underscore == null || underscore.isEmpty()) {
            return underscore;
        }
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (int i = 0; i < underscore.length(); i++) {
            char ch = underscore.charAt(i);
            if (ch == '_') {
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(ch));
                nextUpper = false;
            } else if (i == 0) {
                sb.append(Character.toLowerCase(ch));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    // ==================== 截断 ====================

    /**
     * 截断字符串，超出长度加省略号
     */
    public static String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...";
    }

    /**
     * 安全截断（不截断多字节字符中间）
     */
    public static String truncateSafe(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        // 从 maxLen 处反向扫描，确保不截断在 supplementary 字符中间
        int idx = maxLen;
        if (idx > 0 && Character.isHighSurrogate(text.charAt(idx - 1))) {
            idx--;
        }
        return text.substring(0, idx) + "...";
    }

    // ==================== 校验 ====================

    public static boolean isPhone(String s) {
        return s != null && s.matches("^1[3-9]\\d{9}$");
    }

    public static boolean isEmail(String s) {
        return s != null && s.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    }

    /**
     * 首字母大写
     */
    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * 首字母小写
     */
    public static String uncapitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
