package com.liang.xz.aiagent.llm;

import com.liang.xz.aiagent.config.AiProperties;
import com.liang.xz.aiagent.config.DbProviderConfig;
import com.liang.xz.aiagent.repository.DbProviderConfigRepository;
import com.liang.xz.common.core.crypto.CryptoManager;
import com.liang.xz.common.core.util.BeijingTimeUtil;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * <p>供应商故障切换服务 — 从数据库动态读取供应商配置，主供应商优先，模型调用失败时自动切换到备用供应商</p>
 *
 * <p>切换策略：
 * <ol>
 *   <li>从 sys_ai_provider 读取所有 enabled=1 的供应商，按 is_primary DESC、priority ASC 排序</li>
 *   <li>列表中第一个即为主供应商，其余为备用供应商</li>
 *   <li>非流式调用：按顺序尝试执行，任一调用成功即停止并返回结果</li>
 *   <li>流式调用：按顺序尝试，在发出第一个 token 之前失败的供应商会被跳过，切换到下一个</li>
 *   <li>数据库无可用供应商时，回退到 application.yml / Nacos 中的静态 ai-agent.llm 配置</li>
 * </ol>
 * </p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
public class ProviderFailoverService {

    private final DbProviderConfigRepository configRepository;
    private final CryptoManager cryptoManager;
    private final AiProperties aiProperties;

    public ProviderFailoverService(DbProviderConfigRepository configRepository,
                                   CryptoManager cryptoManager,
                                   AiProperties aiProperties) {
        this.configRepository = configRepository;
        this.cryptoManager = cryptoManager;
        this.aiProperties = aiProperties;
    }

    /**
     * 供应商运行时信息
     */
    @Data
    @Builder
    public static class ProviderInfo {
        private String providerCode;
        private String providerName;
        private String providerType;
        private String modelUsed;
        private boolean primary;
        private int priority;
    }

    /**
     * 带工具能力的 ChatModel 及对应的工具规格。
     */
    @Data
    @Builder
    public static class ChatModelWithTools {
        /** 构建完成的 ChatModel */
        private OpenAiChatModel chatModel;
        /** 由工具对象转换而来的工具规格列表 */
        private List<ToolSpecification> toolSpecifications;
        /** 实际命中的供应商编码 */
        private String providerCode;
        /** 实际命中的供应商名称 */
        private String providerName;
        /** 实际命中的供应商类型 */
        private String providerType;
        /** 实际使用的模型名 */
        private String modelUsed;
    }

    /**
     * 获取排序后的供应商列表（主供应商排在第一位）
     *
     * @param preferredProvider 优先使用的供应商编码（可选）；若存在且启用，则放到首位
     */
    public List<DbProviderConfig> getSortedProviders(String preferredProvider) {
        List<DbProviderConfig> providers = configRepository.findAllEnabled();
        if (providers.isEmpty()) {
            // 供应商完全由页面配置（数据库 sys_ai_provider 管理），不再回退静态配置
            return Collections.emptyList();
        }
        if (preferredProvider == null || preferredProvider.isBlank()) {
            return providers;
        }
        // 将指定供应商提到队首，其余保持原有主备顺序
        List<DbProviderConfig> sorted = new ArrayList<>(providers);
        Optional<DbProviderConfig> preferred = sorted.stream()
                .filter(p -> preferredProvider.equalsIgnoreCase(p.getProviderCode()))
                .findFirst();
        if (preferred.isPresent()) {
            sorted.remove(preferred.get());
            sorted.add(0, preferred.get());
        }
        return sorted;
    }

    /**
     * 获取主供应商（is_primary=1 / priority 最小的启用供应商）。
     * 用于视觉/多模态等直接依赖某个具体供应商能力的场景。
     *
     * @return 主供应商配置，无可用供应商时返回 null
     */
    public DbProviderConfig getPrimaryProvider() {
        List<DbProviderConfig> providers = configRepository.findAllEnabled();
        return providers.isEmpty() ? null : providers.get(0);
    }

    /**
     * 获取排序后的供应商列表（主供应商排在第一位）
     */
    public List<DbProviderConfig> getSortedProviders() {
        return getSortedProviders(null);
    }

    /**
     * 依据供应商编码与模型名解析供应商元信息，供调用日志记录使用。
     *
     * <p>当 {@code preferredProvider} 为空时回退到主供应商；若数据库无配置则返回空元信息。
     * 返回对象永不为 {@code null}，但所有字段均可能为空（仅当完全无可用供应商时）。</p>
     *
     * @param preferredProvider 优先供应商编码（可为空）
     * @param modelName         模型名（可为空，仅用于补充 modelUsed）
     * @return 供应商元信息
     */
    public ProviderInfo resolveProviderInfo(String preferredProvider, String modelName) {
        List<DbProviderConfig> providers = getSortedProviders(preferredProvider);
        if (providers.isEmpty()) {
            return ProviderInfo.builder().build();
        }
        DbProviderConfig provider = providers.get(0);
        String actualModel = resolveActualModel(provider, modelName);
        return ProviderInfo.builder()
                .providerCode(provider.getProviderCode())
                .providerName(provider.getProviderName())
                .providerType(provider.getProviderType())
                .modelUsed(actualModel)
                .build();
    }

    /**
     * 非流式调用：带执行层故障切换。
     *
     * <p>失败处理策略：
     * <ol>
     *   <li>对当前供应商先在业务层进行 {@code maxRetries} 次指数退避重试，避免瞬时抖动误切换</li>
     *   <li>当前供应商重试全部失败后再切换到下一个备用供应商，对备用供应商同样进行重试</li>
     *   <li>所有供应商都失败时抛出 {@link IllegalStateException}</li>
     * </ol></p>
     *
     * @param preferredProvider 优先使用的供应商编码（可选）
     * @param modelName         目标模型名（为空则使用供应商默认模型）
     * @param temperature       温度参数
     * @param maxTokens         最大 Token 数
     * @param executor          实际调用逻辑
     * @param providerInfo      出参：实际使用的供应商信息
     * @return executor 的返回值
     */
    public <T> T executeWithFailover(String preferredProvider, String modelName, double temperature, int maxTokens,
                                    Function<OpenAiChatModel, T> executor,
                                    AtomicReference<ProviderInfo> providerInfo) {
        List<DbProviderConfig> providers = getSortedProviders(preferredProvider);
        if (providers.isEmpty()) {
            throw new IllegalStateException("没有可用的 AI 供应商，请先配置至少一个供应商并启用");
        }

        Exception lastError = null;
        for (int i = 0; i < providers.size(); i++) {
            DbProviderConfig provider = providers.get(i);
            String actualModel = resolveActualModel(provider, modelName);
            int retries = Math.max(0, provider.getMaxRetries());

            // 对当前供应商进行业务层显式重试（指数退避），避免瞬时网络抖动导致立即切换
            for (int attempt = 0; attempt <= retries; attempt++) {
                try {
                    if (attempt > 0) {
                        long backoffMs = (long) (Math.pow(2, attempt - 1) * 500L);
                        log.info("[ProviderFailover] 供应商 {} 第 {}/{} 次重试，等待 {}ms 后重试",
                                provider.getProviderName(), attempt, retries, backoffMs);
                        try {
                            Thread.sleep(backoffMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("供应商重试被中断", ie);
                        }
                    }
                    log.info("[ProviderFailover] 尝试调用供应商: {} (isPrimary={}, attempt={}/{}), model={}",
                            provider.getProviderName(), provider.isPrimary(), attempt + 1, retries + 1, actualModel);

                    OpenAiChatModel model = doBuildChatModel(provider, actualModel, temperature, maxTokens);
                    T result = executor.apply(model);

                    if (i > 0) {
                        log.warn("[ProviderFailover] 主供应商重试耗尽，已切换到备用供应商: {} (priority={})",
                                provider.getProviderName(), provider.getPriority());
                    } else if (attempt > 0) {
                        log.warn("[ProviderFailover] 主供应商 {} 第 {} 次重试成功，已恢复响应",
                                provider.getProviderName(), attempt);
                    }

                    if (providerInfo != null) {
                        providerInfo.set(toProviderInfo(provider, actualModel));
                    }
                    return result;
                } catch (Exception e) {
                    lastError = e;
                    log.warn("[ProviderFailover] 时间={} 供应商 {} ({}, model={}) 调用失败 [attempt={}/{}]: {}",
                            BeijingTimeUtil.formatNow(),
                            provider.getProviderName(), provider.getBaseUrl(), actualModel,
                            attempt + 1, retries + 1, e.getMessage(), e);
                }
            }
        }

        log.error("[ProviderFailover] 时间={} 所有 AI 供应商重试后仍不可用，共尝试了 {} 个供应商，最后一次失败原因={}",
                BeijingTimeUtil.formatNow(), providers.size(),
                lastError != null ? lastError.getMessage() : "未知错误", lastError);
        throw new IllegalStateException(
                "所有 AI 供应商重试后仍不可用，共尝试了 " + providers.size() + " 个供应商", lastError);
    }

    /**
     * 流式调用：带执行层故障切换。
     *
     * <p>失败处理策略（关键：<b>必须等到某个供应商真正成功产生结果后才把数据交付给用户</b>）：
     * <ol>
     *   <li>对当前供应商先在业务层最多重试 {@code maxRetries} 次</li>
     *   <li>在<b>首个 token 发出之前</b>失败的供应商：错误不会透传给用户，继续重试下一个 attempt</li>
     *   <li>当前供应商全部重试失败后，<b>切换备用供应商</b>并继续重试，直到某个供应商成功产出</li>
     *   <li>只有某个供应商 {@code onComplete} 真正成功时，才把 token / 完成事件交付给 {@code userHandler}</li>
     *   <li>所有供应商+重试都失败，才向用户侧 {@code onError} 抛出最后一次失败原因</li>
     *   <li>若首个 token 已经发出后失败（流式固有约束，无法无缝切换），才立即把错误透传给用户</li>
     * </ol></p>
     *
     * @param preferredProvider 优先使用的供应商编码（可选）
     * @param modelName         目标模型名
     * @param temperature       温度参数
     * @param maxTokens         最大 Token 数
     * @param messages          待发送的消息
     * @param userHandler       用户侧流式回调
     * @param providerInfo      出参：实际使用的供应商信息
     */
    public void executeStreamingWithFailover(String preferredProvider, String modelName, double temperature, int maxTokens,
                                            List<dev.langchain4j.data.message.ChatMessage> messages,
                                            StreamingResponseHandler<AiMessage> userHandler,
                                            AtomicReference<ProviderInfo> providerInfo) {
        List<DbProviderConfig> providers = getSortedProviders(preferredProvider);
        if (providers.isEmpty()) {
            throw new IllegalStateException("没有可用的 AI 供应商，请先配置至少一个供应商并启用");
        }

        Throwable lastFailure = null;
        for (int i = 0; i < providers.size(); i++) {
            DbProviderConfig provider = providers.get(i);
            String actualModel = resolveActualModel(provider, modelName);
            int retries = Math.max(0, provider.getMaxRetries());
            final int providerIndex = i;

            for (int attempt = 0; attempt <= retries; attempt++) {
                final int currentAttempt = attempt;
                if (currentAttempt > 0) {
                    long backoffMs = (long) (Math.pow(2, currentAttempt - 1) * 500L);
                    log.info("[ProviderFailover] 流式供应商 {} 第 {}/{} 次重试，等待 {}ms 后重试",
                            provider.getProviderName(), currentAttempt, retries, backoffMs);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("供应商重试被中断", ie);
                    }
                }
                log.info("[ProviderFailover] 尝试流式调用供应商: {} (isPrimary={}, attempt={}/{}), model={}",
                        provider.getProviderName(), provider.isPrimary(), currentAttempt + 1, retries + 1, actualModel);

                OpenAiStreamingChatModel model = doBuildStreamingChatModel(provider, actualModel, temperature, maxTokens);
                AtomicBoolean tokenEmitted = new AtomicBoolean(false);
                AtomicBoolean successRef = new AtomicBoolean(false);
                AtomicBoolean deliverOnSuccess = new AtomicBoolean(attempt > 0 || providerIndex > 0);
                AtomicReference<ProviderInfo> localInfo = new AtomicReference<>(toProviderInfo(provider, actualModel));
                AtomicReference<Throwable> failureRef = new AtomicReference<>();
                AtomicReference<Response<AiMessage>> successResponseRef = new AtomicReference<>();
                // 用于缓冲"非主供应商首次"的 token，待确认成功后再 flush 给用户，
                // 避免重试/切换时把多个供应商的 token 堆叠给同一个 userHandler。
                List<String> bufferedTokens = Collections.synchronizedList(new ArrayList<>());
                // OpenAiStreamingChatModel.generate 是异步的，必须等待流真正结束（onComplete/onError）
                // 才能判定本次尝试成功与否，否则会误判为失败而重复重试、并导致内容堆叠、成功日志缺失。
                CountDownLatch doneLatch = new CountDownLatch(1);

                StreamingResponseHandler<AiMessage> wrapper = new StreamingResponseHandler<>() {
                    @Override
                    public void onNext(String token) {
                        tokenEmitted.set(true);
                        if (providerInfo != null) {
                            providerInfo.set(localInfo.get());
                        }
                        if (deliverOnSuccess.get()) {
                            // 重试/切换场景：先收进 buffer，确认成功后再推送
                            bufferedTokens.add(token);
                        } else {
                            // 主供应商首次：逐字即时推送，保留流式体验
                            userHandler.onNext(token);
                        }
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        successRef.set(Boolean.TRUE);
                        successResponseRef.set(response);
                        doneLatch.countDown();
                    }

                    @Override
                    public void onError(Throwable error) {
                        failureRef.set(error);
                        if (!tokenEmitted.get()) {
                            // 第一个 token 之前失败：不向用户透传，由外层重试/切换备用供应商
                            log.warn("[ProviderFailover] 供应商 {} 流式调用在首 token 前失败 (attempt={}/{}): {}",
                                    provider.getProviderName(), currentAttempt + 1, retries + 1, error.getMessage());
                        } else {
                            // 首个 token 已发出后失败（无法无缝切换），立即把错误透传给用户
                            userHandler.onError(error);
                        }
                        doneLatch.countDown();
                    }
                };

                model.generate(messages, wrapper);
                awaitStreamDone(doneLatch, provider, failureRef);

                if (providerInfo != null && providerInfo.get() == null) {
                    providerInfo.set(localInfo.get());
                }

                if (tokenEmitted.get() && !successRef.get() && failureRef.get() != null) {
                    // 首 token 已发出后失败，错误已在 onError 中透传给用户，不再重试/切换
                    return;
                }

                if (successRef.get()) {
                    // 真正成功产生结果：先把 buffer 中的 token 顺序 flush 给用户（保证只来自一次成功的流）
                    if (deliverOnSuccess.get() && !bufferedTokens.isEmpty()) {
                        for (String t : bufferedTokens) {
                            userHandler.onNext(t);
                        }
                        bufferedTokens.clear();
                    }
                    if (providerIndex > 0) {
                        log.warn("[ProviderFailover] 主供应商流式调用失败，已切换到备用供应商并成功: {} (priority={})",
                                provider.getProviderName(), provider.getPriority());
                    } else if (currentAttempt > 0) {
                        log.warn("[ProviderFailover] 主供应商 {} 流式第 {} 次重试成功，已恢复响应",
                                provider.getProviderName(), currentAttempt);
                    }
                    userHandler.onComplete(successResponseRef.get());
                    return;
                }

                // 本次未成功（首 token 前失败），丢弃 buffer，记录失败原因并继续重试/切换备用
                bufferedTokens.clear();
                lastFailure = failureRef.get();
            }
        }

        // 所有供应商 + 重试均失败，向用户侧抛出最后一次失败原因
        Throwable finalFailure = lastFailure != null ? lastFailure
                : new IllegalStateException("所有 AI 供应商重试后仍不可用（流式）");
        log.error("[ProviderFailover] 时间={} 所有 {} 个供应商流式调用均失败，最后一次失败原因={}",
                BeijingTimeUtil.formatNow(), providers.size(),
                finalFailure != null ? finalFailure.getMessage() : "未知错误", finalFailure);
        userHandler.onError(finalFailure);
    }

    /**
     * 等待流式调用真正结束（onComplete 或 onError 回调触发）。
     *
     * <p>超时时间取供应商配置的 timeoutSeconds 并额外预留 10 秒缓冲；
     * 超时视为本次尝试失败，由外层继续重试或切换备用供应商。</p>
     *
     * @param doneLatch  流结束闭锁
     * @param provider   当前供应商配置
     * @param failureRef 失败原因出参
     */
    private void awaitStreamDone(CountDownLatch doneLatch, DbProviderConfig provider,
                                 AtomicReference<Throwable> failureRef) {
        long waitSeconds = Math.max(provider.getTimeoutSeconds(), 1) + 10L;
        try {
            if (!doneLatch.await(waitSeconds, TimeUnit.SECONDS)) {
                failureRef.compareAndSet(null,
                        new IllegalStateException("供应商 " + provider.getProviderName()
                                + " 流式响应超时（" + waitSeconds + "s 内未结束）"));
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待流式响应被中断", ie);
        }
    }

    /**
     * 构建 ChatModel（仅构建，不做执行层切换，向后兼容）
     */
    public OpenAiChatModel buildChatModel(String preferredProvider, String modelName, double temperature, int maxTokens) {
        AtomicReference<ProviderInfo> providerInfo = new AtomicReference<>();
        return executeWithFailover(preferredProvider, modelName, temperature, maxTokens, model -> model, providerInfo);
    }

    /**
     * 构建带工具(Function Calling)支持的 ChatModel。
     *
     * <p>优先选择首选供应商，直接传入已由 {@link ToolSpecifications} 生成的工具规格。
     * 由调用方负责工具执行循环（解析 toolExecutionRequests 并回灌结果）。</p>
     *
     * @param preferredProvider 优先供应商编码
     * @param modelName         模型名
     * @param temperature       温度
     * @param maxTokens         最大 token
     * @param toolSpecs         工具规格列表（来自 {@code @Tool} 方法）
     * @return 支持 function calling 的 ChatModel 及对应的工具规格
     */
    public ChatModelWithTools buildChatModelWithTools(String preferredProvider, String modelName,
                                                      double temperature, int maxTokens,
                                                      List<ToolSpecification> toolSpecs) {
        List<DbProviderConfig> providers = getSortedProviders(preferredProvider);
        if (providers.isEmpty()) {
            throw new IllegalStateException("没有可用的 AI 供应商");
        }
        DbProviderConfig provider = providers.get(0);
        return buildChatModelWithTools(provider, modelName, temperature, maxTokens, toolSpecs);
    }

    /**
     * 构建所有可用供应商的带工具 ChatModel，按优先级排序。
     *
     * <p>用于主调用失败时按优先级切换供应商重试。返回列表永不为空，若为空则抛出异常。</p>
     *
     * @param preferredProvider 优先供应商编码
     * @param modelName         模型名
     * @param temperature       温度
     * @param maxTokens         最大 token
     * @param toolSpecs         工具规格列表
     * @return 可用供应商模型列表
     */
    public List<ChatModelWithTools> buildAllChatModelsWithTools(String preferredProvider, String modelName,
                                                                double temperature, int maxTokens,
                                                                List<ToolSpecification> toolSpecs) {
        List<DbProviderConfig> providers = getSortedProviders(preferredProvider);
        if (providers.isEmpty()) {
            throw new IllegalStateException("没有可用的 AI 供应商");
        }
        List<ChatModelWithTools> result = new ArrayList<>(providers.size());
        for (DbProviderConfig provider : providers) {
            try {
                result.add(buildChatModelWithTools(provider, modelName, temperature, maxTokens, toolSpecs));
            } catch (Exception e) {
                log.warn("[ProviderFailoverService] 构建供应商 {} 模型失败，跳过: {}",
                        provider.getProviderCode(), e.getMessage());
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("所有 AI 供应商模型构建均失败");
        }
        return result;
    }

    private ChatModelWithTools buildChatModelWithTools(DbProviderConfig provider, String modelName,
                                                       double temperature, int maxTokens,
                                                       List<ToolSpecification> toolSpecs) {
        String actualModel = resolveActualModel(provider, modelName);
        OpenAiChatModel chatModel = doBuildChatModel(provider, actualModel, temperature, maxTokens);
        List<ToolSpecification> specs = toolSpecs != null ? toolSpecs : Collections.emptyList();
        return ChatModelWithTools.builder()
                .chatModel(chatModel)
                .toolSpecifications(specs)
                .providerCode(provider.getProviderCode())
                .providerName(provider.getProviderName())
                .providerType(provider.getProviderType())
                .modelUsed(actualModel)
                .build();
    }

    /**
     * 构建 Streaming ChatModel（仅构建，向后兼容）
     */
    public OpenAiStreamingChatModel buildStreamingChatModel(String preferredProvider, String modelName, double temperature, int maxTokens) {
        List<DbProviderConfig> providers = getSortedProviders(preferredProvider);
        if (providers.isEmpty()) {
            throw new IllegalStateException("没有可用的 AI 供应商");
        }
        DbProviderConfig provider = providers.get(0);
        String actualModel = resolveActualModel(provider, modelName);
        return doBuildStreamingChatModel(provider, actualModel, temperature, maxTokens);
    }

    // ==================== 内部构建方法 ====================

    private OpenAiChatModel doBuildChatModel(DbProviderConfig provider, String actualModel,
                                              double temperature, int maxTokens) {
        String apiKey = decryptKey(provider.getApiKey());
        return OpenAiChatModel.builder()
                .baseUrl(normalizeBaseUrl(provider.getBaseUrl(), provider.getProviderType()))
                .apiKey(apiKey)
                .modelName(actualModel)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(provider.getTimeoutSeconds()))
                // 框架层重试关闭，统一在 ProviderFailoverService 业务层控制重试/切换策略
                .maxRetries(0)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    private OpenAiStreamingChatModel doBuildStreamingChatModel(DbProviderConfig provider, String actualModel,
                                                                double temperature, int maxTokens) {
        String apiKey = decryptKey(provider.getApiKey());
        return OpenAiStreamingChatModel.builder()
                .baseUrl(normalizeBaseUrl(provider.getBaseUrl(), provider.getProviderType()))
                .apiKey(apiKey)
                .modelName(actualModel)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(provider.getTimeoutSeconds()))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    private String resolveActualModel(DbProviderConfig provider, String modelName) {
        if (modelName != null && !modelName.isBlank()) {
            return modelName;
        }
        return (provider.getDefaultModel() != null && !provider.getDefaultModel().isBlank())
                ? provider.getDefaultModel() : "gpt-4o";
    }

    private ProviderInfo toProviderInfo(DbProviderConfig provider, String actualModel) {
        return ProviderInfo.builder()
                .providerCode(provider.getProviderCode())
                .providerName(provider.getProviderName())
                .providerType(provider.getProviderType())
                .modelUsed(actualModel)
                .primary(provider.isPrimary())
                .priority(provider.getPriority())
                .build();
    }

    private String decryptKey(String encryptedKey) {
        if (encryptedKey == null) {
            return "";
        }
        try {
            return cryptoManager.decrypt(encryptedKey);
        } catch (Exception e) {
            log.warn("[ProviderFailover] API Key 解密失败，使用原始值: {}", e.getMessage());
            return encryptedKey;
        }
    }

    /**
     * 解密供应商的 API Key（供需要直接读取供应商配置的组件，如 LlmClient 视觉能力使用）。
     *
     * @param provider 供应商配置（apiKey 为密文）
     * @return 解密后的明文 API Key
     */
    public String decryptApiKeyProvider(DbProviderConfig provider) {
        return decryptKey(provider.getApiKey());
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * 规范化 Base URL。
     */
    private String normalizeBaseUrl(String baseUrl, String providerType) {
        if (baseUrl == null) {
            return null;
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if ("openai".equalsIgnoreCase(providerType) && !hasOpenAiApiSuffix(normalized)) {
            normalized = normalized + "/v1";
        }
        return normalized;
    }

    private boolean hasOpenAiApiSuffix(String url) {
        return url.endsWith("/v1")
                || url.contains("/v1/")
                || url.contains("/openai/deployments")
                || url.contains("/compatible-mode")
                || url.contains("/paas/v4");
    }
}
