package com.liang.xz.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.system.dto.AiInvokeLogPageQuery;
import com.liang.xz.system.dto.AiInvokeLogResponse;
import com.liang.xz.system.dto.AiInvokeLogStatsResponse;
import com.liang.xz.system.dto.PageResponse;
import com.liang.xz.system.entity.SysAiInvokeLogEntity;
import com.liang.xz.system.repository.AiInvokeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>AI 调用记录服务实现</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiInvokeLogService implements IAiInvokeLogService {

    private final AiInvokeLogRepository aiInvokeLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PageResponse<AiInvokeLogResponse> page(AiInvokeLogPageQuery query) {
        int page = (query.getPage() == null || query.getPage() < 1) ? 1 : query.getPage();
        int size = (query.getSize() == null || query.getSize() < 1) ? 10 : query.getSize();
        int offset = (page - 1) * size;

        long total = aiInvokeLogRepository.count(
                query.getProviderCode(), query.getModelName(), query.getUserId(),
                query.getSuccess(), query.getStartTime(), query.getEndTime());
        if (total == 0) {
            return PageResponse.empty(page, size);
        }

        List<SysAiInvokeLogEntity> entities = aiInvokeLogRepository.pageQuery(
                offset, size, query.getProviderCode(), query.getModelName(),
                query.getUserId(), query.getSuccess(), query.getStartTime(), query.getEndTime());

        List<AiInvokeLogResponse> records = new ArrayList<>(entities.size());
        for (SysAiInvokeLogEntity e : entities) {
            records.add(toResponse(e));
        }
        return PageResponse.of(records, total, page, size);
    }

    @Override
    public AiInvokeLogResponse detail(Long id) {
        SysAiInvokeLogEntity e = aiInvokeLogRepository.findById(id);
        return e == null ? null : toResponse(e);
    }

    @Override
    public AiInvokeLogStatsResponse stats(LocalDateTime start, LocalDateTime end) {
        AiInvokeLogRepository.AiInvokeLogStats s = aiInvokeLogRepository.stats(start, end);
        AiInvokeLogStatsResponse r = new AiInvokeLogStatsResponse();
        if (s == null) {
            r.setTotal(0L);
            r.setSuccess(0L);
            r.setCacheHit(0L);
            r.setTotalTokens(0L);
            r.setAvgElapsedMs(0.0);
            r.setCacheHitRate(0.0);
            r.setSuccessRate(0.0);
            return r;
        }
        r.setTotal(s.total);
        r.setSuccess(s.success);
        r.setCacheHit(s.cacheHit);
        r.setTotalTokens(s.totalTokens);
        r.setAvgElapsedMs(s.avgElapsedMs);
        r.setCacheHitRate(s.cacheHitRate());
        r.setSuccessRate(s.successRate());
        return r;
    }

    @Override
    public void delete(Long id) {
        aiInvokeLogRepository.deleteById(id);
    }

    @Override
    public void clear() {
        aiInvokeLogRepository.clearAll();
    }

    private AiInvokeLogResponse toResponse(SysAiInvokeLogEntity e) {
        AiInvokeLogResponse r = new AiInvokeLogResponse();
        r.setId(e.getId());
        r.setSessionId(e.getSessionId());
        r.setUserId(e.getUserId());
        // 若 JOIN 命中到 sys_user，则把姓名也带回前端
        // 找不到用户时 userName 为 null，前端会回退展示 userId
        r.setUserName(e.getUserName());
        r.setProviderCode(e.getProviderCode());
        r.setProviderName(e.getProviderName());
        r.setProviderType(e.getProviderType());
        r.setModelName(e.getModelName());
        r.setComplexity(e.getComplexity());
        r.setCached(e.getCached());
        r.setPromptTokens(e.getPromptTokens());
        r.setCompletionTokens(e.getCompletionTokens());
        r.setTotalTokens(e.getTotalTokens());
        r.setToolNames(parseJsonArray(e.getToolNames()));
        r.setToolCount(e.getToolCount());
        r.setRagReferences(parseJsonArray(e.getRagReferences()));
        r.setContextContent(e.getContextContent());
        r.setUserInput(e.getUserInput());
        r.setAiOutput(e.getAiOutput());
        r.setElapsedMs(e.getElapsedMs());
        r.setSuccess(e.getSuccess());
        r.setErrorMsg(e.getErrorMsg());
        r.setInvokeTime(e.getInvokeTime());
        r.setCreateTime(e.getCreateTime());
        return r;
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.debug("[AiInvokeLog] 解析 JSON 数组失败: {}", json);
            return Collections.emptyList();
        }
    }
}
