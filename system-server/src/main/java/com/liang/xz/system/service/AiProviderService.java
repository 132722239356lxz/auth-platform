package com.liang.xz.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.common.core.crypto.CryptoManager;
import com.liang.xz.system.dto.AiProviderOptionResponse;
import com.liang.xz.system.dto.AiProviderPageQuery;
import com.liang.xz.system.dto.AiProviderRequest;
import com.liang.xz.system.dto.AiProviderResponse;
import com.liang.xz.system.dto.PageResponse;
import com.liang.xz.system.dto.ProviderTestRequest;
import com.liang.xz.system.dto.ProviderTestResponse;
import com.liang.xz.system.entity.SysAiProviderEntity;
import com.liang.xz.system.repository.AiComplexityRoutingRepository;
import com.liang.xz.system.repository.AiProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <p>AI 供应商配置服务</p>
 *
 * <p>密钥统一由 {@link CryptoManager} 加密后落库，出参一律脱敏；
 * 编辑时密钥留空或回传掩码串都表示"保持原值不变"。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiProviderService implements IAiProviderService {

    /** 掩码占位串，同时作为"未修改密钥"的识别特征 */
    private static final String MASK_BODY = "****";

    /** 短密钥全部打码的长度阈值 */
    private static final int MASK_MIN_LENGTH = 8;

    /** 掩码保留的首尾明文长度 */
    private static final int MASK_KEEP_LENGTH = 4;

    /** 密钥解密失败时的兜底展示 */
    private static final String MASK_UNREADABLE = "********";

    private static final BigDecimal DEFAULT_TEMPERATURE = new BigDecimal("0.70");

    private static final int TEMPERATURE_SCALE = 2;

    private final AiProviderRepository aiProviderRepository;

    private final AiComplexityRoutingRepository aiComplexityRoutingRepository;

    private final CryptoManager cryptoManager;

    private final IAiProviderProbe aiProviderProbe;

    private final ObjectMapper objectMapper;

    @Override
    public PageResponse<AiProviderResponse> page(AiProviderPageQuery query) {
        long total = aiProviderRepository.count(query);
        if (total == 0) {
            return PageResponse.empty(query.getPage(), query.getPageSize());
        }
        List<AiProviderResponse> records = aiProviderRepository.findPage(query).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return PageResponse.of(records, total, query.getPage(), query.getPageSize());
    }

    @Override
    public AiProviderResponse getById(Long id) {
        return aiProviderRepository.findById(id)
                .map(this::toResponse)
                .orElse(null);
    }

    @Override
    public List<AiProviderOptionResponse> listOptions() {
        return aiProviderRepository.findAllEnabled().stream()
                .map(this::toOption)
                .collect(Collectors.toList());
    }

    @Override
    public List<AiProviderOptionResponse> listAvailableOptions(Long excludeRoutingId) {
        return aiProviderRepository.findAvailable(excludeRoutingId).stream()
                .map(this::toOption)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AiProviderResponse create(AiProviderRequest request) {
        validateCommon(request);
        if (isBlank(request.getApiKey()) || request.getApiKey().contains(MASK_BODY)) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
        String providerCode = request.getProviderCode().trim();
        if (aiProviderRepository.existsByProviderCode(providerCode, null)) {
            throw new IllegalArgumentException("供应商编码已存在: " + providerCode);
        }

        SysAiProviderEntity entity = toEntity(request, providerCode);
        entity.setApiKey(cryptoManager.encrypt(request.getApiKey().trim()));
        entity.setSecretKey(isBlank(request.getSecretKey())
                ? null : cryptoManager.encrypt(request.getSecretKey().trim()));

        Long id;
        try {
            id = aiProviderRepository.insert(entity);
            // 如果新供应商设为主供应商，清除其他供应商的主标记
            if (Boolean.TRUE.equals(entity.getIsPrimary())) {
                aiProviderRepository.clearOtherPrimaryFlags(id);
            }
        } catch (DuplicateKeyException e) {
            // 并发场景下前置校验可能失效，依赖 uk_provider_code 兜底
            throw new IllegalArgumentException("供应商编码已存在: " + providerCode);
        }
        log.info("创建 AI 供应商配置成功: id={}, providerCode={}, isPrimary={}", id, providerCode, entity.getIsPrimary());
        return getById(id);
    }

    @Override
    @Transactional
    public AiProviderResponse update(Long id, AiProviderRequest request) {
        Optional<SysAiProviderEntity> existingOpt = aiProviderRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return null;
        }
        validateCommon(request);
        String providerCode = request.getProviderCode().trim();
        if (aiProviderRepository.existsByProviderCode(providerCode, id)) {
            throw new IllegalArgumentException("供应商编码已存在: " + providerCode);
        }

        SysAiProviderEntity existing = existingOpt.get();
        String apiKeyCipher = resolveSecret(request.getApiKey(), existing.getApiKey());
        if (isBlank(apiKeyCipher)) {
            throw new IllegalArgumentException("API Key 不能为空");
        }

        SysAiProviderEntity entity = toEntity(request, providerCode);
        entity.setId(id);
        entity.setApiKey(apiKeyCipher);
        entity.setSecretKey(Boolean.TRUE.equals(request.getClearSecretKey())
                ? null : resolveSecret(request.getSecretKey(), existing.getSecretKey()));

        try {
            aiProviderRepository.update(entity);
            // 如果当前供应商设为主供应商，清除其他供应商的主标记
            if (Boolean.TRUE.equals(entity.getIsPrimary())) {
                aiProviderRepository.clearOtherPrimaryFlags(id);
            }
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("供应商编码已存在: " + providerCode);
        }
        log.info("更新 AI 供应商配置成功: id={}, providerCode={}, isPrimary={}", id, providerCode, entity.getIsPrimary());
        return getById(id);
    }

    @Override
    @Transactional
    public boolean delete(Long id) {
        if (aiComplexityRoutingRepository.existsByProviderId(id)) {
            throw new IllegalArgumentException("该供应商已被路由配置引用，请先删除对应的复杂度路由");
        }
        boolean removed = aiProviderRepository.deleteById(id) > 0;
        if (removed) {
            log.info("删除 AI 供应商配置成功: id={}", id);
        }
        return removed;
    }

    @Override
    public ProviderTestResponse test(ProviderTestRequest request) {
        String providerType = request.getProviderType();
        String baseUrl = request.getBaseUrl();
        String apiKey = request.getApiKey();
        Integer timeoutMs = request.getTimeoutMs();

        // 表单未改动密钥时前端只回传掩码串，此时回退到库里已保存的配置
        if (request.getProviderId() != null) {
            SysAiProviderEntity saved = aiProviderRepository.findById(request.getProviderId()).orElse(null);
            if (saved != null) {
                if (isBlank(providerType)) {
                    providerType = saved.getProviderType();
                }
                if (isBlank(baseUrl)) {
                    baseUrl = saved.getBaseUrl();
                }
                if (timeoutMs == null) {
                    timeoutMs = saved.getTimeoutMs();
                }
                if (isBlank(apiKey) || apiKey.contains(MASK_BODY)) {
                    apiKey = decryptQuietly(saved.getApiKey());
                }
            }
        }
        if (isBlank(apiKey)) {
            return ProviderTestResponse.fail(null, 0L, "API Key 不能为空，请先填写或保存供应商配置");
        }
        return aiProviderProbe.probe(providerType, baseUrl, apiKey, timeoutMs);
    }

    @Override
    public ProviderTestResponse testById(Long id) {
        SysAiProviderEntity entity = aiProviderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("供应商配置不存在: " + id));
        String apiKey = decryptQuietly(entity.getApiKey());
        if (isBlank(apiKey)) {
            return ProviderTestResponse.fail(null, 0L, "已保存的 API Key 无法解密，请重新录入");
        }
        return aiProviderProbe.probe(entity.getProviderType(), entity.getBaseUrl(), apiKey, entity.getTimeoutMs());
    }

    /**
     * 组装除密钥外的实体字段，密钥由调用方按新增/编辑语义单独设置。
     */
    private SysAiProviderEntity toEntity(AiProviderRequest request, String providerCode) {
        BigDecimal temperature = request.getTemperature() != null
                ? request.getTemperature().setScale(TEMPERATURE_SCALE, RoundingMode.HALF_UP)
                : DEFAULT_TEMPERATURE;
        return SysAiProviderEntity.builder()
                .providerCode(providerCode)
                .providerName(request.getProviderName().trim())
                .providerType(request.getProviderType().trim().toLowerCase(Locale.ROOT))
                .baseUrl(request.getBaseUrl().trim())
                .defaultModel(trimToNull(request.getDefaultModel()))
                .embeddingModel(trimToNull(request.getEmbeddingModel()))
                .models(toModelsJson(request.getModels()))
                .isPrimary(request.getIsPrimary() != null && request.getIsPrimary())
                .priority(request.getPriority() != null ? request.getPriority() : 0)
                .timeoutMs(request.getTimeoutMs())
                .maxRetries(request.getMaxRetries())
                .temperature(temperature)
                .enabled(request.getEnabled() == null || request.getEnabled())
                .remark(trimToNull(request.getRemark()))
                .build();
    }

    private AiProviderResponse toResponse(SysAiProviderEntity entity) {
        AiProviderResponse response = new AiProviderResponse();
        response.setId(entity.getId());
        response.setProviderCode(entity.getProviderCode());
        response.setProviderName(entity.getProviderName());
        response.setProviderType(entity.getProviderType());
        response.setBaseUrl(entity.getBaseUrl());
        response.setApiKeyMasked(maskSecret(entity.getApiKey()));
        response.setApiKeyConfigured(!isBlank(entity.getApiKey()));
        response.setSecretKeyMasked(maskSecret(entity.getSecretKey()));
        response.setSecretKeyConfigured(!isBlank(entity.getSecretKey()));
        response.setDefaultModel(entity.getDefaultModel());
        response.setEmbeddingModel(entity.getEmbeddingModel());
        response.setModels(parseModels(entity.getModels()));
        response.setIsPrimary(entity.getIsPrimary());
        response.setPriority(entity.getPriority());
        response.setTimeoutMs(entity.getTimeoutMs());
        response.setMaxRetries(entity.getMaxRetries());
        response.setTemperature(entity.getTemperature());
        response.setEnabled(entity.getEnabled());
        response.setRemark(entity.getRemark());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

    private AiProviderOptionResponse toOption(SysAiProviderEntity entity) {
        AiProviderOptionResponse option = new AiProviderOptionResponse();
        option.setId(entity.getId());
        option.setProviderCode(entity.getProviderCode());
        option.setProviderName(entity.getProviderName());
        option.setProviderType(entity.getProviderType());
        option.setDefaultModel(entity.getDefaultModel());
        option.setEmbeddingModel(entity.getEmbeddingModel());
        option.setModels(parseModels(entity.getModels()));
        return option;
    }

    private void validateCommon(AiProviderRequest request) {
        requireText(request.getProviderCode(), "供应商编码不能为空");
        requireText(request.getProviderName(), "供应商名称不能为空");
        requireText(request.getProviderType(), "供应商类型不能为空");
        requireText(request.getBaseUrl(), "Base URL 不能为空");
        String baseUrl = request.getBaseUrl().trim().toLowerCase(Locale.ROOT);
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw new IllegalArgumentException("Base URL 必须以 http:// 或 https:// 开头");
        }
    }

    /**
     * 解析密钥入参：留空或回传掩码串都表示保持原密文不变，否则加密新值。
     */
    private String resolveSecret(String newValue, String oldCipher) {
        if (isBlank(newValue) || newValue.contains(MASK_BODY)) {
            return oldCipher;
        }
        return cryptoManager.encrypt(newValue.trim());
    }

    /**
     * 密钥脱敏。密文无法解密时不影响列表展示，仅回显固定掩码。
     */
    private String maskSecret(String cipher) {
        if (isBlank(cipher)) {
            return null;
        }
        String plain;
        try {
            plain = cryptoManager.decrypt(cipher);
        } catch (Exception e) {
            log.warn("AI 供应商密钥解密失败，仅回显掩码: {}", e.getMessage());
            return MASK_UNREADABLE;
        }
        if (isBlank(plain)) {
            return null;
        }
        if (plain.length() <= MASK_MIN_LENGTH) {
            return MASK_BODY;
        }
        return plain.substring(0, MASK_KEEP_LENGTH) + MASK_BODY
                + plain.substring(plain.length() - MASK_KEEP_LENGTH);
    }

    /** 解密失败返回 null，由调用方决定如何提示 */
    private String decryptQuietly(String cipher) {
        if (isBlank(cipher)) {
            return null;
        }
        try {
            return cryptoManager.decrypt(cipher);
        } catch (Exception e) {
            log.warn("AI 供应商密钥解密失败: {}", e.getMessage());
            return null;
        }
    }

    private String toModelsJson(List<String> models) {
        if (models == null || models.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> cleaned = models.stream()
                .filter(m -> !isBlank(m))
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(new ArrayList<>(cleaned));
        } catch (Exception e) {
            throw new IllegalArgumentException("模型列表序列化失败: " + e.getMessage());
        }
    }

    /**
     * 解析模型列表。历史数据可能是逗号分隔的字符串，解析失败时降级按逗号切分。
     */
    private List<String> parseModels(String json) {
        if (isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            List<String> models = objectMapper.readValue(json, new TypeReference<List<String>>() { });
            return models != null ? models : Collections.emptyList();
        } catch (Exception e) {
            log.warn("模型列表非合法 JSON，降级为逗号切分: {}", json);
            return Arrays.stream(json.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
    }

    private void requireText(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
