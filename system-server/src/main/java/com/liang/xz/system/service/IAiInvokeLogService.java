package com.liang.xz.system.service;

import com.liang.xz.system.dto.AiInvokeLogPageQuery;
import com.liang.xz.system.dto.AiInvokeLogResponse;
import com.liang.xz.system.dto.AiInvokeLogStatsResponse;
import com.liang.xz.system.dto.PageResponse;

import java.time.LocalDateTime;

/**
 * <p>AI 调用记录服务接口</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public interface IAiInvokeLogService {

    /**
     * 分页查询 AI 调用记录
     */
    PageResponse<AiInvokeLogResponse> page(AiInvokeLogPageQuery query);

    /**
     * 根据 ID 查询单条记录详情
     */
    AiInvokeLogResponse detail(Long id);

    /**
     * 聚合统计（总调用/成功率/缓存命中率/总 token/平均耗时）
     */
    AiInvokeLogStatsResponse stats(LocalDateTime start, LocalDateTime end);

    /**
     * 根据 ID 删除记录
     */
    void delete(Long id);

    /**
     * 清空所有调用记录
     */
    void clear();
}
