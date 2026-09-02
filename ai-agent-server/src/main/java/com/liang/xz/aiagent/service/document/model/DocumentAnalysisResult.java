package com.liang.xz.aiagent.service.document.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>文档分析结果 —— 一次文档深度分析的完整产出</p>
 *
 * <p>除元素序列外，还携带:</p>
 * <ul>
 *   <li><b>统计信息：</b>各类元素的数量，便于判断文档构成（如"扫描件占比 80%"）；</li>
 *   <li><b>处理概况：</b>各处理方式的元素数，反映本次分析的模型调用情况与成本；</li>
 *   <li><b>正文文本：</b>仅正文元素拼成的文本，供分块与向量化直接使用（已排除页眉页脚噪声）。</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
public class DocumentAnalysisResult {

    /** 源文件名 */
    private String fileName;

    /** 检测到的文档格式（PDF / DOCX / DOC / OTHER） */
    private String documentFormat;

    /** 全部元素（按阅读顺序） */
    private List<DocumentElement> elements;

    /** 仅正文元素拼成的文本，供分块入库 */
    private String bodyText;

    /** 总页数 */
    private int pageCount;

    /** 分析耗时毫秒 */
    private long elapsedMs;

    /** 是否调用了视觉模型（用于成本统计） */
    private boolean visionModelUsed;

    /** 视觉模型调用次数 */
    private int visionModelCallCount;

    /** 降级说明：分析失败或部分能力不可用时的原因 */
    private String degradationNote;

    // ======================== 派生统计 ========================

    /**
     * 按元素类型统计数量。
     */
    public Map<DocumentElement.ElementType, Integer> countByType() {
        Map<DocumentElement.ElementType, Integer> counts =
                new EnumMap<>(DocumentElement.ElementType.class);
        if (elements == null) {
            return counts;
        }
        for (DocumentElement element : elements) {
            if (element.getElementType() == null) {
                continue;
            }
            counts.merge(element.getElementType(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * 按处理方式统计数量，反映本次分析的模型调用分布。
     */
    public Map<DocumentElement.ProcessingMethod, Integer> countByMethod() {
        Map<DocumentElement.ProcessingMethod, Integer> counts =
                new EnumMap<>(DocumentElement.ProcessingMethod.class);
        if (elements == null) {
            return counts;
        }
        for (DocumentElement element : elements) {
            if (element.getProcessingMethod() == null) {
                continue;
            }
            counts.merge(element.getProcessingMethod(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * 取指定类型的元素。
     */
    public List<DocumentElement> elementsOfType(DocumentElement.ElementType type) {
        if (elements == null || type == null) {
            return Collections.emptyList();
        }
        List<DocumentElement> matched = new ArrayList<>();
        for (DocumentElement element : elements) {
            if (type.equals(element.getElementType())) {
                matched.add(element);
            }
        }
        return matched;
    }

    /**
     * 仅返回正文元素。
     */
    public List<DocumentElement> bodyElements() {
        if (elements == null) {
            return Collections.emptyList();
        }
        List<DocumentElement> body = new ArrayList<>();
        for (DocumentElement element : elements) {
            if (element.isBodyContent()) {
                body.add(element);
            }
        }
        return body;
    }

    /**
     * 生成人类可读的分析摘要，用于日志与前端展示。
     */
    public String toSummary() {
        Map<DocumentElement.ElementType, Integer> byType = countByType();
        if (byType.isEmpty()) {
            return "未检测到任何文档元素";
        }
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("SCANNED_PAGE", "扫描页");
        labels.put("TABLE", "表格");
        labels.put("IMAGE", "图片");
        labels.put("CHART", "图表");
        labels.put("HEADING", "标题");
        labels.put("TEXT", "正文段");
        labels.put("HEADER", "页眉");
        labels.put("FOOTER", "页脚");
        labels.put("FOOTNOTE", "脚注");
        labels.put("ANNOTATION", "批注");
        labels.put("WATERMARK", "水印");

        StringBuilder sb = new StringBuilder();
        sb.append("文档分析完成: 格式=").append(documentFormat)
                .append(", 页数=").append(pageCount)
                .append(", 元素=").append(elements == null ? 0 : elements.size())
                .append(" [");
        boolean first = true;
        for (Map.Entry<DocumentElement.ElementType, Integer> entry : byType.entrySet()) {
            if (!first) {
                sb.append(" ");
            }
            first = false;
            sb.append(labels.getOrDefault(entry.getKey().name(), entry.getKey().name()))
                    .append("×").append(entry.getValue());
        }
        sb.append("]");
        if (visionModelUsed) {
            sb.append(", 视觉模型调用 ").append(visionModelCallCount).append(" 次");
        }
        sb.append(", 耗时 ").append(elapsedMs).append("ms");
        return sb.toString();
    }
}
