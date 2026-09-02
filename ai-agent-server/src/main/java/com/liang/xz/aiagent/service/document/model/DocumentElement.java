package com.liang.xz.aiagent.service.document.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>文档元素 —— 文档中一个可独立处理的内容单元</p>
 *
 * <p><b>设计背景：</b>原有 {@code FileParserService} 使用 Tika 的 {@code BodyContentHandler}
 * 抽取纯文本，会把整篇文档压平为一个字符串：表格退化为按单元格顺序拼接的裸文本（丢失行列关系），
 * 内嵌图片完全被丢弃，页眉页脚混入正文。这导致 RAG 检索时无法区分"正文论述"与"页眉的公司名"，
 * 也无法回答"文档中第三个表格的内容是什么"这类问题。</p>
 *
 * <p>本模型把文档还原为<b>带类型与位置的元素序列</b>，使下游可以:</p>
 * <ul>
 *   <li>按类型过滤（检索时排除页眉页脚，避免噪声命中）；</li>
 *   <li>按位置还原阅读顺序（表格保持完整结构，不被拆散）；</li>
 *   <li>按处理方式追溯（哪些内容来自 OCR、置信度如何，便于评估可信度）。</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Builder
public class DocumentElement {

    /** 元素序号，按阅读顺序递增（跨页连续） */
    private int orderIndex;

    /** 元素类型 */
    private ElementType elementType;

    /** 所在页码（从 1 开始；Word 无明确分页时为 0） */
    private int pageNo;

    /**
     * 元素在页面中的位置（归一化坐标，左上为原点，取值范围 0~1）。
     * 顺序：[x0, y0, x1, y1]。无位置信息时为 null。
     */
    private float[] bbox;

    /** 元素文本内容；表格为 Markdown 表格串；图片为视觉模型的描述 */
    private String content;

    /** 该元素采用的处理方式 */
    private ProcessingMethod processingMethod;

    /**
     * 处理置信度（0~1）。
     * 直接提取的文本为 1.0；OCR/视觉模型识别的结果由启发式给出（见各提取器）。
     * 下游可据此过滤低可信内容，或在回答时对低置信度内容加注说明。
     */
    private double confidence;

    /** 备注：如图片尺寸、表格行列数、OCR 失败原因等 */
    private String remark;

    /** 表格专用：表头行（无表头时为空） */
    private List<String> tableHeader;

    /** 表格专用：数据行，每行是一个单元格数组 */
    private List<List<String>> tableRows;

    /** 表格专用：列数 */
    private int tableColumnCount;

    /**
     * 元素是否属于正文（页眉页脚、水印等为 false）。
     * 检索与问答时应以正文内容为准，非正文元素用于补充上下文。
     */
    public boolean isBodyContent() {
        return elementType != null && elementType.isBody();
    }

    /**
     * 获取表格数据的副本（防止外部修改内部状态）。
     */
    public List<List<String>> copyTableRows() {
        if (tableRows == null) {
            return new ArrayList<>();
        }
        List<List<String>> copy = new ArrayList<>(tableRows.size());
        for (List<String> row : tableRows) {
            copy.add(row == null ? new ArrayList<>() : new ArrayList<>(row));
        }
        return copy;
    }

    // ======================== 枚举定义 ========================

    /**
     * 元素类型。
     */
    public enum ElementType {
        /** 普通正文段落 */
        TEXT,
        /** 标题（由样式或字号推断） */
        HEADING,
        /** 表格 */
        TABLE,
        /** 嵌入图片（普通图片、截图） */
        IMAGE,
        /** 图表（由视觉模型判定为图表/曲线图等的图片） */
        CHART,
        /** 扫描件页面（图像型页面，文本层为空，需视觉识别） */
        SCANNED_PAGE,
        /** 页眉 */
        HEADER,
        /** 页脚 */
        FOOTER,
        /** 脚注/尾注 */
        FOOTNOTE,
        /** 批注 */
        ANNOTATION,
        /** 水印 */
        WATERMARK,
        /** 未分类 */
        UNKNOWN;

        /**
         * 是否属于正文内容（用于检索过滤噪声）。
         */
        public boolean isBody() {
            return this == TEXT || this == HEADING || this == TABLE
                    || this == IMAGE || this == CHART || this == SCANNED_PAGE;
        }

        /**
         * 是否需要视觉模型介入才能获取内容。
         */
        public boolean requiresVision() {
            return this == IMAGE || this == CHART || this == SCANNED_PAGE;
        }
    }

    /**
     * 处理方式（记录该元素的内容是如何得到的，用于可信度评估）。
     */
    public enum ProcessingMethod {
        /** 直接从文档文本层提取（最可靠） */
        DIRECT_EXTRACT,
        /** 通过视觉模型识别（图片/扫描件），等价于 OCR + 语义理解 */
        VISION_MODEL,
        /** 表格结构化还原（基于文本位置聚类） */
        TABLE_STRUCTURED,
        /** 由大模型对原始内容做增强理解（如复杂表格语义还原） */
        MODEL_ENHANCED,
        /** 规则推断（如按位置判定页眉页脚） */
        RULE_INFERRED,
        /** 未处理（如超出处理上限、格式不支持） */
        SKIPPED;
    }
}
