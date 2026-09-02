package com.liang.xz.message.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class R<T> {
    private int code;
    private String msg;
    private T data;

    public static <T> R<T> ok() { return new R<>(200, "success", null); }
    public static <T> R<T> ok(T data) { return new R<>(200, "success", data); }
    public static <T> R<T> fail(String msg) { return new R<>(500, msg, null); }
}
