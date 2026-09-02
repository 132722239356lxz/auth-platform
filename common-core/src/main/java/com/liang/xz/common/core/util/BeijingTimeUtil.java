package com.liang.xz.common.core.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * <p>北京时间工具类</p>
 *
 * <p>统一使用 {@code Asia/Shanghai} 时区，避免日志/输出中出现 UTC 或 ISO-8601 带 {@code T} 的时间格式。
 * 默认格式为 {@code yyyy-MM-dd HH:mm:ss}。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public final class BeijingTimeUtil {

    /**
     * 北京时间时区。
     */
    public static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 默认时间格式：yyyy-MM-dd HH:mm:ss。
     */
    public static final DateTimeFormatter DEFAULT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private BeijingTimeUtil() {
    }

    /**
     * 获取当前北京时间。
     *
     * @return 当前北京时间 {@link LocalDateTime}
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(BEIJING_ZONE);
    }

    /**
     * 按默认格式 {@code yyyy-MM-dd HH:mm:ss} 格式化当前北京时间。
     *
     * @return 格式化后的时间字符串
     */
    public static String formatNow() {
        return now().format(DEFAULT_FORMATTER);
    }

    /**
     * 按默认格式 {@code yyyy-MM-dd HH:mm:ss} 格式化指定的时间。
     *
     * @param dateTime 待格式化的时间，可为 {@code null}
     * @return 格式化后的时间字符串；入参为 {@code null} 时返回空串
     */
    public static String format(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DEFAULT_FORMATTER);
    }
}
