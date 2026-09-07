package com.liang.xz.system.service;

import com.liang.xz.system.dto.AiProviderOptionResponse;
import com.liang.xz.system.dto.AiRoutingPageQuery;
import com.liang.xz.system.dto.AiRoutingRequest;
import com.liang.xz.system.dto.AiRoutingResponse;
import com.liang.xz.system.dto.PageResponse;
import com.liang.xz.system.entity.SysAiComplexityRoutingEntity;
import com.liang.xz.system.repository.AiComplexityRoutingRepository;
import com.liang.xz.system.repository.AiProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>AI 复杂度路由配置服务</p>
 *
 * <p>sys_ai_complexity_routing.provider_id 上有唯一约束，一个供应商至多绑定一条路由，
 * 因此新增/编辑都要先做占用校验，并以 DuplicateKeyException 兜底并发写入。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiComplexityRoutingService implements IAiComplexityRoutingService {

    private static final String DUPLICATE_PROVIDER_MESSAGE = "该供应商已存在路由配置，请直接编辑已有配置";

    private final AiComplexityRoutingRepository aiComplexityRoutingRepository;

    private final AiProviderRepository aiProviderRepository;

    private final IAiProviderService aiProviderService;

    @Override
    public PageResponse<AiRoutingResponse> page(AiRoutingPageQuery query) {
        long total = aiComplexityRoutingRepository.count(query);
        if (total == 0) {
            return PageResponse.empty(query.getPage(), query.getPageSize());
        }
        List<AiRoutingResponse> records = aiComplexityRoutingRepository.findPage(query).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return PageResponse.of(records, total, query.getPage(), query.getPageSize());
    }

    @Override
    public AiRoutingResponse getById(Long id) {
        return aiComplexityRoutingRepository.findById(id)
                .map(this::toResponse)
                .orElse(null);
    }

    @Override
    public List<AiProviderOptionResponse> listAvailableProviders(Long excludeRoutingId) {
        return aiProviderService.listAvailableOptions(excludeRoutingId);
    }

    @Override
    @Transactional
    public AiRoutingResponse create(AiRoutingRequest request) {
        validate(request);
        if (aiComplexityRoutingRepository.countByProviderId(request.getProviderId(), null) > 0) {
            throw new IllegalArgumentException(DUPLICATE_PROVIDER_MESSAGE);
        }

        Long id;
        try {
            id = aiComplexityRoutingRepository.insert(toEntity(request));
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException(DUPLICATE_PROVIDER_MESSAGE);
        }
        log.info("创建 AI 复杂度路由成功: id={}, providerId={}", id, request.getProviderId());
        return getById(id);
    }

    @Override
    @Transactional
    public AiRoutingResponse update(Long id, AiRoutingRequest request) {
        if (aiComplexityRoutingRepository.findById(id).isEmpty()) {
            return null;
        }
        validate(request);
        if (aiComplexityRoutingRepository.countByProviderId(request.getProviderId(), id) > 0) {
            throw new IllegalArgumentException(DUPLICATE_PROVIDER_MESSAGE);
        }

        SysAiComplexityRoutingEntity entity = toEntity(request);
        entity.setId(id);
        try {
            aiComplexityRoutingRepository.update(entity);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException(DUPLICATE_PROVIDER_MESSAGE);
        }
        log.info("更新 AI 复杂度路由成功: id={}, providerId={}", id, request.getProviderId());
        return getById(id);
    }

    @Override
    @Transactional
    public boolean delete(Long id) {
        boolean removed = aiComplexityRoutingRepository.deleteById(id) > 0;
        if (removed) {
            log.info("删除 AI 复杂度路由成功: id={}", id);
        }
        return removed;
    }

    private void validate(AiRoutingRequest request) {
        if (request.getRoutingName() == null || request.getRoutingName().trim().isEmpty()) {
            throw new IllegalArgumentException("路由名称不能为空");
        }
        if (request.getProviderId() == null) {
            throw new IllegalArgumentException("请选择关联的供应商");
        }
        if (aiProviderRepository.findById(request.getProviderId()).isEmpty()) {
            throw new IllegalArgumentException("关联的供应商不存在: " + request.getProviderId());
        }
        requireModel(request.getSimpleModel(), "简单任务模型不能为空");
        requireModel(request.getMediumModel(), "中等任务模型不能为空");
        requireModel(request.getComplexModel(), "复杂任务模型不能为空");
    }

    private void requireModel(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    private SysAiComplexityRoutingEntity toEntity(AiRoutingRequest request) {
        return SysAiComplexityRoutingEntity.builder()
                .routingName(request.getRoutingName().trim())
                .providerId(request.getProviderId())
                .enabled(request.getEnabled() == null || request.getEnabled())
                .simpleModel(request.getSimpleModel().trim())
                .mediumModel(request.getMediumModel().trim())
                .complexModel(request.getComplexModel().trim())
                .simpleMaxTokens(request.getSimpleMaxTokens())
                .mediumMaxTokens(request.getMediumMaxTokens())
                .complexMaxTokens(request.getComplexMaxTokens())
                .remark(trimToNull(request.getRemark()))
                .build();
    }

    private AiRoutingResponse toResponse(SysAiComplexityRoutingEntity entity) {
        AiRoutingResponse response = new AiRoutingResponse();
        response.setId(entity.getId());
        response.setRoutingName(entity.getRoutingName());
        response.setProviderId(entity.getProviderId());
        response.setProviderCode(entity.getProviderCode());
        response.setProviderName(entity.getProviderName());
        response.setSimpleModel(entity.getSimpleModel());
        response.setMediumModel(entity.getMediumModel());
        response.setComplexModel(entity.getComplexModel());
        response.setSimpleMaxTokens(entity.getSimpleMaxTokens());
        response.setMediumMaxTokens(entity.getMediumMaxTokens());
        response.setComplexMaxTokens(entity.getComplexMaxTokens());
        response.setEnabled(entity.getEnabled());
        response.setRemark(entity.getRemark());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
