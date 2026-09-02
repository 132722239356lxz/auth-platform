package com.liang.xz.log.enums;

import lombok.Getter;

/**
 * 日志分类 - 按业务场景区分
 *
 * @author liang
 */
@Getter
public enum LogCategory {

    APPLICATION("application", "应用日志"),
    REQUEST("request", "请求日志"),
    SECURITY("security", "安全认证日志"),
    DATABASE("database", "数据库日志"),
    MQ("mq", "消息队列日志"),
    CACHE("cache", "缓存日志"),
    PERFORMANCE("performance", "性能日志(慢操作)"),
    EXCEPTION("exception", "异常日志"),
    SCHEDULED("scheduled", "定时任务日志"),
    INTEGRATION("integration", "外部集成日志");

    private final String code;
    private final String label;

    LogCategory(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static LogCategory fromCode(String code) {
        for (LogCategory c : values()) {
            if (c.code.equalsIgnoreCase(code)) {
                return c;
            }
        }
        return APPLICATION;
    }
}
