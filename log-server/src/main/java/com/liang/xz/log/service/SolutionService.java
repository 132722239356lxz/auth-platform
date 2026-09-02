package com.liang.xz.log.service;

import com.liang.xz.log.dto.SolutionSaveRequest;
import com.liang.xz.log.entity.ErrorSolution;
import com.liang.xz.log.repository.ErrorSolutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 错误解决方案知识库管理服务
 *
 * @author liang
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SolutionService {

    private final ErrorSolutionRepository errorSolutionRepository;

    /**
     * 保存或更新解决方案 (按指纹去重)
     */
    public ErrorSolution saveOrUpdate(SolutionSaveRequest req) {
        Optional<ErrorSolution> existing = errorSolutionRepository
                .findByFingerprint(req.getErrorFingerprint());

        ErrorSolution entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.setErrorPattern(req.getErrorPattern());
            entity.setRootCause(req.getRootCause());
            entity.setSolution(req.getSolution());
            entity.setSteps(req.getSteps());
            entity.setReferenceUrl(req.getReferenceUrl());
            entity.setStatus(req.getStatus() != null ? req.getStatus() : "PUBLISHED");
            entity.setUpdateTime(LocalDateTime.now());
        } else {
            entity = ErrorSolution.builder()
                    .errorFingerprint(req.getErrorFingerprint())
                    .errorPattern(req.getErrorPattern())
                    .rootCause(req.getRootCause())
                    .solution(req.getSolution())
                    .steps(req.getSteps())
                    .referenceUrl(req.getReferenceUrl())
                    .resolveCount(0)
                    .status(req.getStatus() != null ? req.getStatus() : "PUBLISHED")
                    .createdBy("admin")
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
        }

        ErrorSolution saved = errorSolutionRepository.save(entity);
        log.info("[log-server] 解决方案已保存: fingerprint={}", req.getErrorFingerprint());
        return saved;
    }

    public Optional<ErrorSolution> findByFingerprint(String fingerprint) {
        return errorSolutionRepository.findByFingerprint(fingerprint);
    }

    public void incrementResolveCount(String fingerprint) {
        errorSolutionRepository.findByFingerprint(fingerprint).ifPresent(s -> {
            errorSolutionRepository.incrementResolveCount(s.getId());
            log.info("[log-server] 解决方案计数+1: fingerprint={}", fingerprint);
        });
    }

    public List<ErrorSolution> findTopSolutions(int limit) {
        return errorSolutionRepository.findTopSolutions(limit);
    }
}
