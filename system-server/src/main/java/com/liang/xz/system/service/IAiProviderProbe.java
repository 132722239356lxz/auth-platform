package com.liang.xz.system.service;

import com.liang.xz.system.dto.ProviderTestResponse;

/**
 * <p>AI 供应商连通性探测接口</p>
 *
 * <p>探测失败属于业务结果而非接口错误, 实现类统一以 {@link ProviderTestResponse} 返回, 不抛异常。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public interface IAiProviderProbe {

    /**
     * 探测供应商连通性。
     *
     * @param providerType 供应商类型
     * @param baseUrl API 基础地址
     * @param apiKey API Key(明文或密文, 由调用方决定)
     * @param timeoutMs 超时时间(毫秒)
     * @return 探测结果
     */
    ProviderTestResponse probe(String providerType, String baseUrl, String apiKey, Integer timeoutMs);
}
