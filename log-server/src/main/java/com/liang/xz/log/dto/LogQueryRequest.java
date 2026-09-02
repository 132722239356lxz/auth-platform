package com.liang.xz.log.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 日志查询请求
 *
 * @author liang
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogQueryRequest {

    /** 当前页(默认1) */
    private Integer page = 1;

    /** 每页大小(默认20) */
    private Integer size = 20;

    /** 来源模块 */
    private String module;

    /** 日志分类 */
    private String category;

    /** 日志级别 */
    private String level;

    /** 关键字模糊搜索(消息/traceId) */
    private String keyword;

    /** 异常类型 */
    private String exceptionType;

    /** 错误指纹 */
    private String errorFingerprint;

    /** HTTP状态码 */
    private Integer httpStatus;

    /** 操作人 */
    private String username;

    /** 请求URI */
    private String requestUri;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 最小耗时(ms) */
    private Long minCostTime;

}
