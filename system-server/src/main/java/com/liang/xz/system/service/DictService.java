package com.liang.xz.system.service;

import com.liang.xz.system.dto.DictDataRequest;
import com.liang.xz.system.dto.DictDataResponse;
import com.liang.xz.system.dto.DictTypeRequest;
import com.liang.xz.system.dto.DictTypeResponse;
import com.liang.xz.system.entity.DictDataEntity;
import com.liang.xz.system.entity.DictTypeEntity;
import com.liang.xz.system.repository.DictRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <p>数据字典服务</p>
 *
 * @author auth-platform
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictService implements IDictService {

    private final DictRepository dictRepository;

    // ======================== 字典类型 ========================

    public List<DictTypeResponse> listTypes() {
        return dictRepository.findAllTypes().stream()
                .map(this::toTypeResponse)
                .collect(Collectors.toList());
    }

    public Optional<DictTypeResponse> getTypeById(Long id) {
        return dictRepository.findTypeById(id).map(this::toTypeResponse);
    }

    @Transactional
    public DictTypeResponse createType(DictTypeRequest request) {
        if (dictRepository.findTypeByDictType(request.getDictType()).isPresent()) {
            throw new IllegalArgumentException("字典类型 [" + request.getDictType() + "] 已存在");
        }
        DictTypeEntity entity = DictTypeEntity.builder()
                .dictName(request.getDictName())
                .dictType(request.getDictType())
                .description(request.getDescription())
                .enabled(true)
                .build();
        long id = dictRepository.insertType(entity);
        entity.setId(id);
        log.info("[Dict] 字典类型创建成功: id={}, type={}", id, entity.getDictType());
        return toTypeResponse(entity);
    }

    @Transactional
    public Optional<DictTypeResponse> updateType(Long id, DictTypeRequest request) {
        return dictRepository.findTypeById(id).map(existing -> {
            DictTypeEntity entity = DictTypeEntity.builder()
                    .id(id)
                    .dictName(request.getDictName())
                    .dictType(request.getDictType())
                    .description(request.getDescription())
                    .build();
            dictRepository.updateType(entity);
            log.info("[Dict] 字典类型更新成功: id={}", id);
            return toTypeResponse(entity);
        });
    }

    @Transactional
    public boolean deleteType(Long id) {
        dictRepository.deleteDataByTypeId(id);
        int result = dictRepository.deleteTypeById(id);
        if (result > 0) {
            log.info("[Dict] 字典类型删除成功: id={}", id);
        }
        return result > 0;
    }

    // ======================== 字典数据 ========================

    public List<DictDataResponse> listDataByTypeId(Long typeId) {
        return dictRepository.findDataByTypeId(typeId).stream()
                .map(this::toDataResponse)
                .collect(Collectors.toList());
    }

    /**
     * 按字典类型标识查询数据项（供前端下拉框使用，无需认证）
     */
    public List<DictDataResponse> listDataByDictType(String dictType) {
        return dictRepository.findDataByDictType(dictType).stream()
                .map(this::toDataResponse)
                .collect(Collectors.toList());
    }

    public Optional<DictDataResponse> getDataById(Long id) {
        return dictRepository.findDataById(id).map(this::toDataResponse);
    }

    @Transactional
    public DictDataResponse createData(DictDataRequest request) {
        DictDataEntity entity = DictDataEntity.builder()
                .typeId(request.getTypeId())
                .dictLabel(request.getDictLabel())
                .dictValue(request.getDictValue())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .cssClass(request.getCssClass())
                .listClass(request.getListClass())
                .enabled(true)
                .remark(request.getRemark())
                .build();
        long id = dictRepository.insertData(entity);
        entity.setId(id);
        log.info("[Dict] 字典数据创建成功: id={}, label={}", id, entity.getDictLabel());
        return toDataResponse(entity);
    }

    @Transactional
    public Optional<DictDataResponse> updateData(Long id, DictDataRequest request) {
        return dictRepository.findDataById(id).map(existing -> {
            DictDataEntity entity = DictDataEntity.builder()
                    .id(id)
                    .typeId(request.getTypeId())
                    .dictLabel(request.getDictLabel())
                    .dictValue(request.getDictValue())
                    .sortOrder(request.getSortOrder())
                    .cssClass(request.getCssClass())
                    .listClass(request.getListClass())
                    .remark(request.getRemark())
                    .build();
            dictRepository.updateData(entity);
            log.info("[Dict] 字典数据更新成功: id={}", id);
            return toDataResponse(entity);
        });
    }

    @Transactional
    public boolean deleteData(Long id) {
        int result = dictRepository.deleteDataById(id);
        if (result > 0) {
            log.info("[Dict] 字典数据删除成功: id={}", id);
        }
        return result > 0;
    }

    // ======================== 辅助 ========================

    private DictTypeResponse toTypeResponse(DictTypeEntity e) {
        return DictTypeResponse.builder()
                .id(e.getId())
                .dictName(e.getDictName())
                .dictType(e.getDictType())
                .description(e.getDescription())
                .enabled(e.getEnabled())
                .createTime(e.getCreateTime())
                .build();
    }

    private DictDataResponse toDataResponse(DictDataEntity e) {
        return DictDataResponse.builder()
                .id(e.getId())
                .typeId(e.getTypeId())
                .dictLabel(e.getDictLabel())
                .dictValue(e.getDictValue())
                .sortOrder(e.getSortOrder())
                .cssClass(e.getCssClass())
                .listClass(e.getListClass())
                .enabled(e.getEnabled())
                .remark(e.getRemark())
                .createTime(e.getCreateTime())
                .build();
    }
}
