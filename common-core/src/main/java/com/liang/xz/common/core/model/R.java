package com.liang.xz.common.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>统一响应体 — log-server / auth-flow / auth-message / ai-agent-server 使用</p>
 *
 * <p>对应前端类型: R&lt;T&gt;</p>
 *
 * @param <T> 数据类型
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> implements Serializable {

    /** 状态码 (200=成功) */
    private int code;

    /** 提示消息 */
    private String msg;

    /** 响应数据 */
    private T data;

    /** 是否成功 */
    private Boolean success;

    private R() {
    }

    private R(int code, String msg, T data, Boolean success) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.success = success;
    }

    // ======================== 静态工厂方法 ========================

    public static <T> R<T> ok(T data) {
        return new R<>(200, "success", data, true);
    }

    public static <T> R<T> ok(String msg, T data) {
        return new R<>(200, msg, data, true);
    }

    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, msg, null, false);
    }

    public static <T> R<T> fail(String msg) {
        return new R<>(500, msg, null, false);
    }
}
