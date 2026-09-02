package com.liang.xz.system.service;

import com.liang.xz.system.dto.DeptRequest;
import com.liang.xz.system.dto.DeptResponse;
import com.liang.xz.system.dto.DeptTreeQuery;

import java.util.List;

/**
 * <p>部门管理服务接口</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public interface IDeptService {

    /**
     * 获取部门树
     */
    List<DeptResponse> getDeptTree(DeptTreeQuery query);

    /**
     * 按ID查询部门
     */
    DeptResponse getById(Long id);

    /**
     * 根据部门ID获取部门名称
     */
    String getDeptNameById(Long deptId);

    /**
     * 创建部门
     */
    DeptResponse create(DeptRequest request);

    /**
     * 更新部门
     */
    DeptResponse update(Long id, DeptRequest request);

    /**
     * 启用部门
     */
    void enable(Long id);

    /**
     * 禁用部门
     */
    void disable(Long id);

    /**
     * 删除部门
     */
    void delete(Long id);
}
