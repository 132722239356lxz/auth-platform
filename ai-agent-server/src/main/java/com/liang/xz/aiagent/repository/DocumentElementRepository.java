package com.liang.xz.aiagent.repository;

import com.liang.xz.aiagent.service.document.model.DocumentAnalysisResult;
import com.liang.xz.aiagent.service.document.model.DocumentElement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * <p>文档元素元数据仓储</p>
 *
 * <p>负责把深度分析产出的元素元数据落库，并支持按文档/类型查询。
 * 落库失败只记录告警，不抛出异常——元数据是增强信息，不应阻断文档入库主流程。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Repository
public class DocumentElementRepository {

    /** 元素内容的最大入库长度 */
    private static final int MAX_CONTENT_LENGTH = 30000;

    private final JdbcTemplate jdbcTemplate;

    public DocumentElementRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 批量保存文档元素元数据。
     *
     * @param docId  关联文档ID
     * @param result 分析结果
     * @return 成功写入的条数
     */
    public int saveElements(Long docId, DocumentAnalysisResult result) {
        if (docId == null || result == null || result.getElements() == null
                || result.getElements().isEmpty()) {
            return 0;
        }
        int saved = 0;
        for (DocumentElement element : result.getElements()) {
            try {
                float[] bbox = element.getBbox();
                jdbcTemplate.update(
                        "INSERT INTO ai_document_element (doc_id, order_index, element_type, page_no, "
                                + "bbox_x0, bbox_y0, bbox_x1, bbox_y1, processing_method, confidence, "
                                + "content, table_rows, table_columns, remark, create_time) "
                                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?, NOW())",
                        docId,
                        element.getOrderIndex(),
                        element.getElementType() == null ? "UNKNOWN" : element.getElementType().name(),
                        element.getPageNo(),
                        bbox != null && bbox.length > 0 ? bbox[0] : null,
                        bbox != null && bbox.length > 1 ? bbox[1] : null,
                        bbox != null && bbox.length > 2 ? bbox[2] : null,
                        bbox != null && bbox.length > 3 ? bbox[3] : null,
                        element.getProcessingMethod() == null
                                ? "SKIPPED" : element.getProcessingMethod().name(),
                        element.getConfidence(),
                        truncate(element.getContent(), MAX_CONTENT_LENGTH),
                        element.getTableRows() == null ? 0 : element.getTableRows().size(),
                        element.getTableColumnCount(),
                        truncate(element.getRemark(), 500));
                saved++;
            } catch (Exception e) {
                log.warn("[DocElement] 写入元素元数据失败(不影响主流程): docId={}, order={}, reason={}",
                        docId, element.getOrderIndex(), e.getMessage());
            }
        }
        return saved;
    }

    /**
     * 查询文档的全部元素元数据。
     */
    public List<Map<String, Object>> findByDocId(Long docId) {
        if (docId == null) {
            return List.of();
        }
        try {
            return jdbcTemplate.queryForList(
                    "SELECT id, order_index, element_type, page_no, bbox_x0, bbox_y0, bbox_x1, bbox_y1, "
                            + "processing_method, confidence, left(content, 1000) AS content, "
                            + "table_rows, table_columns, remark, create_time "
                            + "FROM ai_document_element WHERE doc_id = ? ORDER BY order_index ASC", docId);
        } catch (Exception e) {
            log.warn("[DocElement] 查询元素元数据失败: docId={}, reason={}", docId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 删除文档的元素元数据（文档重新解析或删除时使用）。
     */
    public int deleteByDocId(Long docId) {
        if (docId == null) {
            return 0;
        }
        try {
            return jdbcTemplate.update("DELETE FROM ai_document_element WHERE doc_id = ?", docId);
        } catch (Exception e) {
            log.warn("[DocElement] 删除元素元数据失败: docId={}, reason={}", docId, e.getMessage());
            return 0;
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max) + "...[截断]";
    }
}
