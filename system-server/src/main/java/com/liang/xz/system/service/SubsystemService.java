package com.liang.xz.system.service;

import com.liang.xz.common.core.entity.Oauth2ClientSubsystem;
import com.liang.xz.common.core.repository.Oauth2ClientSubsystemRepository;
import com.liang.xz.system.dto.SubsystemRequest;
import com.liang.xz.system.dto.SubsystemResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 客户端子系统管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubsystemService {

    private final Oauth2ClientSubsystemRepository repository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 查询某客户端下的所有子系统
     */
    public List<SubsystemResponse> listByClientId(String clientId) {
        return repository.findByClientId(clientId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 查询所有子系统
     */
    public List<SubsystemResponse> listAll() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 门户首页：查询当前用户可见的子系统列表。
     * <p>
     * 优先读取 sys_user_subsystem 用户-应用关联表，
     * 若该用户无显式关联数据，则降级返回所有启用的门户可见子系统。
     * </p>
     */
    public List<SubsystemResponse> listPortalSubsystems(Long userId) {
        if (userId == null) {
            return List.of();
        }

        // 1. 查询用户显式授权可见的 client_id 列表
        List<String> grantedClientIds = jdbcTemplate.queryForList(
                "SELECT client_id FROM sys_user_subsystem WHERE user_id = ? AND visible = 1",
                String.class, userId);

        List<Oauth2ClientSubsystem> subsystems;
        if (grantedClientIds.isEmpty()) {
            // 2. 无显式关联时，返回所有启用的门户可见子系统
            subsystems = repository.findAllVisible();
        } else {
            // 3. 有显式关联时，仅返回这些 client 下的门户可见子系统
            subsystems = repository.findAllVisible().stream()
                    .filter(s -> grantedClientIds.contains(s.getClientId()))
                    .toList();
        }

        return subsystems.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 按ID查询子系统
     */
    public Optional<SubsystemResponse> getById(Long id) {
        return repository.findById(id).map(this::toResponse);
    }

    /**
     * 创建子系统
     */
    @Transactional
    public SubsystemResponse create(SubsystemRequest request) {
        Oauth2ClientSubsystem entity = Oauth2ClientSubsystem.builder()
                .clientId(request.getClientId())
                .code(request.getCode() != null ? request.getCode() : "web")
                .name(request.getName())
                .iconUrl(request.getIconUrl())
                .redirectUri(request.getRedirectUri())
                .description(request.getDescription())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .visiblePortal(request.getVisiblePortal() != null ? request.getVisiblePortal() : true)
                .build();
        Long id = repository.insert(entity);
        entity.setId(id);
        log.info("[Subsystem] 创建子系统: clientId={}, code={}, name={}, id={}",
                entity.getClientId(), entity.getCode(), entity.getName(), id);
        return toResponse(entity);
    }

    /**
     * 更新子系统
     */
    @Transactional
    public Optional<SubsystemResponse> update(Long id, SubsystemRequest request) {
        return repository.findById(id).map(entity -> {
            entity.setCode(request.getCode() != null ? request.getCode() : "web");
            entity.setName(request.getName());
            entity.setIconUrl(request.getIconUrl());
            entity.setRedirectUri(request.getRedirectUri());
            entity.setDescription(request.getDescription());
            entity.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
            entity.setVisiblePortal(request.getVisiblePortal() != null ? request.getVisiblePortal() : true);
            repository.update(entity);
            log.info("[Subsystem] 更新子系统: id={}, code={}, name={}", id, entity.getCode(), entity.getName());
            return toResponse(entity);
        });
    }

    /**
     * 删除子系统
     */
    @Transactional
    public boolean deleteById(Long id) {
        int rows = repository.deleteById(id);
        if (rows > 0) {
            log.info("[Subsystem] 删除子系统: id={}", id);
        }
        return rows > 0;
    }

    /**
     * 删除某客户端下的所有子系统
     */
    @Transactional
    public int deleteByClientId(String clientId) {
        int rows = repository.deleteByClientId(clientId);
        if (rows > 0) {
            log.info("[Subsystem] 删除客户端所有子系统: clientId={}, rows={}", clientId, rows);
        }
        return rows;
    }

    private SubsystemResponse toResponse(Oauth2ClientSubsystem entity) {
        // 查询客户端名称
        String clientName = queryClientName(entity.getClientId());
        return SubsystemResponse.builder()
                .id(entity.getId())
                .clientId(entity.getClientId())
                .clientName(clientName)
                .code(entity.getCode())
                .name(entity.getName())
                .iconUrl(entity.getIconUrl())
                .redirectUri(entity.getRedirectUri())
                .description(entity.getDescription())
                .sortOrder(entity.getSortOrder())
                .visiblePortal(entity.getVisiblePortal())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    private String queryClientName(String clientId) {
        List<String> names = jdbcTemplate.queryForList(
                "SELECT client_name FROM oauth2_registered_client WHERE client_id = ?",
                String.class, clientId);
        return names.isEmpty() ? null : names.get(0);
    }
}
