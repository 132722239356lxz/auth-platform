package com.liang.xz.log.enums;

import lombok.Getter;

/**
 * 日志来源模块枚举
 * <p>
 * 用于精确区分日志属于哪个微服务或中间件, 方便后续按模块维度分析
 *
 * @author liang
 */
@Getter
public enum ModuleType {

    AUTH_SERVER("auth-server", "授权服务"),
    SYSTEM_SERVER("system-server", "系统管理服务"),
    GATEWAY("gateway", "API 网关"),
    AUTH_FLOW("auth-flow", "授权流程服务"),
    AUTH_MESSAGE("auth-message", "消息服务"),
    AI_AGENT("ai-agent-server", "AI Agent 服务"),
    MQ_PRODUCER("mq-producer", "消息队列生产者"),
    MQ_CONSUMER("mq-consumer", "消息队列消费者"),
    SCHEDULER("scheduler", "定时任务"),
    UNKNOWN("unknown", "未知来源");

    private final String code;
    private final String label;

    ModuleType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static ModuleType fromCode(String code) {
        for (ModuleType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
