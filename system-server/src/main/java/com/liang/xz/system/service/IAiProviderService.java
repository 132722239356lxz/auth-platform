package com.liang.xz.system.service;

import com.liang.xz.system.dto.AiProviderOptionResponse;
import com.liang.xz.system.dto.AiProviderPageQuery;
import com.liang.xz.system.dto.AiProviderRequest;
import com.liang.xz.system.dto.AiProviderResponse;
import com.liang.xz.system.dto.PageResponse;
import com.liang.xz.system.dto.ProviderTestRequest;
import com.liang.xz.system.dto.ProviderTestResponse;

import java.util.List;

/**
 * <p>AI 模型供应商配置服务接口</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public interface IAiProviderService {

    /**
     * 分页查询供应商列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResponse<AiProviderResponse> page(AiProviderPageQuery query);

    /**
     * 根据主键查询供应商详情(密钥以掩码返回)。
     *
     * @param id 主键
     * @return 供应商响应
     */
    AiProviderResponse getById(Long id);

    /**
     * 查询所有启用状态的供应商下拉选项。
     *
     * @return 选项列表
     */
    List<AiProviderOptionResponse> listOptions();

    /**
     * 查询可选供应商下拉选项(可排除指定路由ID)。
     *
     * @param excludeRoutingId 排除的路由ID
     * @return 选项列表
     */
    List<AiProviderOptionResponse> listAvailableOptions(Long excludeRoutingId);

    /**
     * 新增供应商。
     *
     * @param request 请求体
     * @return 供应商响应
     */
    AiProviderResponse create(AiProviderRequest request);

    /**
     * 更新供应商。
     *
     * @param id 主键
     * @param request 请求体
     * @return 供应商响应
     */
    AiProviderResponse update(Long id, AiProviderRequest request);

    /**
     * 删除供应商。
     *
     * @param id 主键
     * @return 是否删除成功
     */
    boolean delete(Long id);

    /**
     * 连通性探测。
     *
     * @param request 探测请求
     * @return 探测结果
     */
    ProviderTestResponse test(ProviderTestRequest request);

    /**
     * 基于已存在供应商配置进行连通性探测。
     *
     * @param id 供应商主键
     * @return 探测结果
     */
    ProviderTestResponse testById(Long id);
}
