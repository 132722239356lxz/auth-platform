package com.liang.xz.system.service;

import com.liang.xz.system.dto.AiProviderOptionResponse;
import com.liang.xz.system.dto.AiRoutingPageQuery;
import com.liang.xz.system.dto.AiRoutingRequest;
import com.liang.xz.system.dto.AiRoutingResponse;
import com.liang.xz.system.dto.PageResponse;

import java.util.List;

/**
 * <p>AI 复杂度路由配置服务接口</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public interface IAiComplexityRoutingService {

    /**
     * 分页查询路由列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResponse<AiRoutingResponse> page(AiRoutingPageQuery query);

    /**
     * 根据主键查询路由详情。
     *
     * @param id 主键
     * @return 路由响应
     */
    AiRoutingResponse getById(Long id);

    /**
     * 查询可选供应商列表(用于路由绑定, 可排除指定路由ID)。
     *
     * @param excludeRoutingId 编辑场景下需排除的路由ID
     * @return 可选供应商选项列表
     */
    List<AiProviderOptionResponse> listAvailableProviders(Long excludeRoutingId);

    /**
     * 新增路由。
     *
     * @param request 请求体
     * @return 路由响应
     */
    AiRoutingResponse create(AiRoutingRequest request);

    /**
     * 更新路由。
     *
     * @param id 主键
     * @param request 请求体
     * @return 路由响应
     */
    AiRoutingResponse update(Long id, AiRoutingRequest request);

    /**
     * 删除路由。
     *
     * @param id 主键
     * @return 是否删除成功
     */
    boolean delete(Long id);
}
