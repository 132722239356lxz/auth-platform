package com.liang.xz.system.service;

import com.liang.xz.system.dto.DictDataRequest;
import com.liang.xz.system.dto.DictDataResponse;
import com.liang.xz.system.dto.DictTypeRequest;
import com.liang.xz.system.dto.DictTypeResponse;

import java.util.List;
import java.util.Optional;

/**
 * <p>数据字典服务接口</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public interface IDictService {

    /**
     * 查询所有字典类型
     */
    List<DictTypeResponse> listTypes();

    /**
     * 按字典标识查询数据
     */
    List<DictDataResponse> listDataByDictType(String dictType);

    /**
     * 创建字典类型
     */
    DictTypeResponse createType(DictTypeRequest request);

    /**
     * 更新字典类型
     */
    Optional<DictTypeResponse> updateType(Long id, DictTypeRequest request);

    /**
     * 删除字典类型
     */
    boolean deleteType(Long id);

    /**
     * 创建字典数据
     */
    DictDataResponse createData(DictDataRequest request);

    /**
     * 更新字典数据
     */
    Optional<DictDataResponse> updateData(Long id, DictDataRequest request);

    /**
     * 删除字典数据
     */
    boolean deleteData(Long id);
}
