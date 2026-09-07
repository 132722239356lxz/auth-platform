package com.liang.xz.system.service;

import com.liang.xz.system.dto.DeptRequest;
import com.liang.xz.system.dto.DeptResponse;
import com.liang.xz.system.dto.DeptTreeQuery;
import com.liang.xz.system.entity.DeptEntity;
import com.liang.xz.system.repository.DeptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>部门管理服务</p>
 *
 * @author auth-platform
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeptService implements IDeptService {

    private final DeptRepository deptRepository;

    /**
     * 查询所有部门并构建树形结构（支持按 keyword/enabled 过滤）
     */
    public List<DeptResponse> getDeptTree(DeptTreeQuery query) {
        String keyword = query.getKeyword();
        Boolean enabled = query.getEnabled();
        List<DeptEntity> all = deptRepository.findAll();
        return buildTree(all.stream()
                .filter(e -> enabled == null || e.getEnabled() == enabled)
                .filter(e -> keyword == null || keyword.isBlank()
                        || matchesKeyword(e, keyword))
                .map(this::toResponse)
                .collect(Collectors.toList()));
    }

    private boolean matchesKeyword(DeptEntity e, String keyword) {
        String kw = keyword.toLowerCase();
        return (e.getDeptName() != null && e.getDeptName().toLowerCase().contains(kw))
                || (e.getDeptCode() != null && e.getDeptCode().toLowerCase().contains(kw))
                || (e.getLeader() != null && e.getLeader().toLowerCase().contains(kw));
    }

    /**
     * 扁平查询所有部门（用于下拉选择）
     */
    public List<DeptResponse> listAll() {
        return deptRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public DeptResponse getById(Long id) {
        return deptRepository.findById(id).map(this::toResponse).orElse(null);
    }

    @Transactional
    public DeptResponse create(DeptRequest request) {
        if (deptRepository.existsByCode(request.getDeptCode())) {
            throw new IllegalArgumentException("部门编码已存在: " + request.getDeptCode());
        }
        DeptEntity dept = DeptEntity.builder()
                .parentId(request.getParentId() != null ? request.getParentId() : 0L)
                .deptName(request.getDeptName())
                .deptCode(request.getDeptCode())
                .leader(request.getLeader())
                .phone(request.getPhone())
                .email(request.getEmail())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();
        deptRepository.insert(dept);
        return deptRepository.findByCode(request.getDeptCode()).map(this::toResponse).orElse(null);
    }

    @Transactional
    public DeptResponse update(Long id, DeptRequest request) {
        DeptEntity existing = deptRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("部门不存在: " + id));
        DeptEntity other = deptRepository.findByCode(request.getDeptCode()).orElse(null);
        if (other != null && !other.getId().equals(id)) {
            throw new IllegalArgumentException("部门编码已存在: " + request.getDeptCode());
        }
        existing.setParentId(request.getParentId() != null ? request.getParentId() : 0L);
        existing.setDeptName(request.getDeptName());
        existing.setDeptCode(request.getDeptCode());
        existing.setLeader(request.getLeader());
        existing.setPhone(request.getPhone());
        existing.setEmail(request.getEmail());
        existing.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        existing.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        deptRepository.update(existing);
        return toResponse(existing);
    }

    @Transactional
    public void enable(Long id) {
        DeptEntity dept = deptRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("部门不存在: " + id));
        dept.setEnabled(true);
        deptRepository.update(dept);
    }

    @Transactional
    public void disable(Long id) {
        DeptEntity dept = deptRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("部门不存在: " + id));
        dept.setEnabled(false);
        deptRepository.update(dept);
    }

    @Transactional
    public void delete(Long id) {
        if (!deptRepository.existsById(id)) {
            throw new IllegalArgumentException("部门不存在: " + id);
        }
        if (deptRepository.countChildren(id) > 0) {
            throw new IllegalArgumentException("该部门下存在子部门，无法删除");
        }
        if (deptRepository.countUsers(id) > 0) {
            throw new IllegalArgumentException("该部门下存在用户，无法删除");
        }
        deptRepository.deleteById(id);
    }

    public String getDeptNameById(Long deptId) {
        if (deptId == null) return null;
        return deptRepository.findById(deptId).map(DeptEntity::getDeptName).orElse(null);
    }

    // ======================== 辅助方法 ========================

    private DeptResponse toResponse(DeptEntity e) {
        return DeptResponse.builder()
                .id(e.getId())
                .parentId(e.getParentId())
                .deptName(e.getDeptName())
                .deptCode(e.getDeptCode())
                .leader(e.getLeader())
                .phone(e.getPhone())
                .email(e.getEmail())
                .sortOrder(e.getSortOrder())
                .enabled(e.getEnabled())
                .createTime(e.getCreateTime())
                .updateTime(e.getUpdateTime())
                .build();
    }

    private List<DeptResponse> buildTree(List<DeptResponse> list) {
        if (list == null || list.isEmpty()) return Collections.emptyList();
        // 按 sortOrder 排序
        list.sort((a, b) -> {
            int cmp = Integer.compare(a.getSortOrder() != null ? a.getSortOrder() : 0,
                    b.getSortOrder() != null ? b.getSortOrder() : 0);
            if (cmp != 0) return cmp;
            return Long.compare(a.getId(), b.getId());
        });
        java.util.Map<Long, DeptResponse> map = new java.util.HashMap<>();
        for (DeptResponse d : list) {
            map.put(d.getId(), d);
        }
        List<DeptResponse> roots = new ArrayList<>();
        for (DeptResponse d : list) {
            if (d.getParentId() == null || d.getParentId() == 0L) {
                roots.add(d);
            } else {
                DeptResponse parent = map.get(d.getParentId());
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(d);
                } else {
                    // 父节点不存在，作为顶级处理
                    roots.add(d);
                }
            }
        }
        return roots;
    }
}
