package com.liang.xz.aiagent.service.document.extractor;

import com.liang.xz.aiagent.service.document.model.DocumentAnalysisProperties;
import com.liang.xz.aiagent.service.document.model.DocumentElement;
import com.liang.xz.aiagent.service.document.model.DocumentElement.ElementType;
import com.liang.xz.aiagent.service.document.model.DocumentElement.ProcessingMethod;
import com.liang.xz.aiagent.service.document.model.VisionProcessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ICell;
import org.apache.poi.xwpf.usermodel.IRunElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFHeaderFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>Word 元素提取器 —— 基于 Apache POI 的结构化提取</p>
 *
 * <p><b>与 PDF 的差异：</b>DOCX 是结构化格式，正文段落、表格、页眉页脚、嵌入图片在对象模型中
 * 都有明确定义（{@code XWPFParagraph} / {@code XWPFTable} / {@code XWPFHeader} / {@code XWPFPicture}），
 * 无需像 PDF 那样靠坐标启发式推断，因此识别准确率更高。</p>
 *
 * <p><b>关键点：必须遍历 BodyElements 而非分别取段落和表格。</b>
 * {@code document.getParagraphs()} 会漏掉表格，而分别取两者会丢失它们的相对顺序，
 * 导致"表格前后的说明文字"错位。{@code getBodyElements()} 能按文档实际顺序返回。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
public class WordElementExtractor {

    /** 图片最小尺寸（像素），过小的多为装饰元素 */
    private static final int MIN_IMAGE_SIZE = 40;

    private final DocumentAnalysisProperties properties;
    private final VisionProcessor visionProcessor;

    public WordElementExtractor(DocumentAnalysisProperties properties, VisionProcessor visionProcessor) {
        this.properties = properties;
        this.visionProcessor = visionProcessor;
    }

    /**
     * 提取 DOCX 中的全部元素。
     */
    public List<DocumentElement> extract(byte[] docxData) {
        List<DocumentElement> elements = new ArrayList<>();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxData))) {
            // 1. 页眉页脚与脚注：单独提取并标记，不混入正文
            elements.addAll(extractHeadersFooters(document));
            elements.addAll(extractFootnotes(document));

            // 2. 正文：按文档顺序遍历，表格与段落交替出现时保持相对位置
            int imageCount = 0;
            for (IBodyElement bodyElement : document.getBodyElements()) {
                if (bodyElement instanceof XWPFParagraph paragraph) {
                    DocumentElement paragraphElement = extractParagraph(paragraph);
                    if (paragraphElement != null) {
                        elements.add(paragraphElement);
                    }
                    // 段落中可能内嵌图片（Word 中图片依附于段落的 run）
                    if (imageCount < properties.getMaxImagesPerDocument()) {
                        List<DocumentElement> images = extractParagraphImages(paragraph);
                        for (DocumentElement image : images) {
                            if (imageCount >= properties.getMaxImagesPerDocument()) {
                                break;
                            }
                            elements.add(image);
                            imageCount++;
                        }
                    }
                } else if (bodyElement instanceof XWPFTable table) {
                    DocumentElement tableElement = extractTable(table);
                    if (tableElement != null) {
                        elements.add(tableElement);
                    }
                }
            }
            reindex(elements);
        } catch (Exception e) {
            log.warn("[WordExtractor] DOCX 解析失败: {}", e.getMessage());
        }
        return elements;
    }

    // ======================== 页眉页脚 ========================

    /**
     * 提取页眉页脚。
     *
     * <p>与 PDF 靠位置猜测不同，DOCX 的页眉页脚有明确对象，
     * 因此这里能准确分离——这正是"结构化格式"的优势。</p>
     */
    private List<DocumentElement> extractHeadersFooters(XWPFDocument document) {
        List<DocumentElement> result = new ArrayList<>();
        try {
            for (XWPFHeaderFooter header : document.getHeaderList()) {
                String text = extractHeaderFooterText(header);
                if (text != null && !text.isBlank()) {
                    result.add(DocumentElement.builder()
                            .elementType(ElementType.HEADER)
                            .pageNo(0)
                            .content(text)
                            .processingMethod(ProcessingMethod.DIRECT_EXTRACT)
                            .confidence(1.0)
                            .remark("Word 页眉对象")
                            .build());
                }
            }
            for (XWPFHeaderFooter footer : document.getFooterList()) {
                String text = extractHeaderFooterText(footer);
                if (text != null && !text.isBlank()) {
                    result.add(DocumentElement.builder()
                            .elementType(ElementType.FOOTER)
                            .pageNo(0)
                            .content(text)
                            .processingMethod(ProcessingMethod.DIRECT_EXTRACT)
                            .confidence(1.0)
                            .remark("Word 页脚对象")
                            .build());
                }
            }
        } catch (Exception e) {
            log.debug("[WordExtractor] 页眉页脚提取失败: {}", e.getMessage());
        }
        return result;
    }

    private String extractHeaderFooterText(XWPFHeaderFooter headerFooter) {
        StringBuilder sb = new StringBuilder();
        for (XWPFParagraph paragraph : headerFooter.getParagraphs()) {
            String text = paragraph.getText();
            if (text != null && !text.isBlank()) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(text.trim());
            }
        }
        for (XWPFTable table : headerFooter.getTables()) {
            String tableText = headerFooterTableText(table);
            if (!tableText.isBlank()) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(tableText);
            }
        }
        return sb.toString();
    }

    /**
     * 提取页眉页脚中表格的文本。
     *
     * <p>页眉页脚中的表格通常用于排版（如"标题 + 页码"分列），而非承载数据，
     * 因此只需按行拼接文本即可，无需像正文表格那样保留完整 Markdown 结构。</p>
     */
    private String headerFooterTableText(XWPFTable table) {
        StringBuilder sb = new StringBuilder();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                String text = cell.getText();
                if (text != null && !text.isBlank()) {
                    cells.add(text.trim().replace("\n", " "));
                }
            }
            if (!cells.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(String.join(" | ", cells));
            }
        }
        return sb.toString();
    }

    // ======================== 脚注/尾注 ========================

    /**
     * 提取脚注与尾注。
     *
     * <p><b>为什么单独处理：</b>脚注通常是对正文术语的补充说明或引用出处，
     * 若混入正文会打断论述连贯性（分块时尤其明显）；但内容本身有价值，
     * 因此作为独立元素记录，检索时可作为补充上下文而非正文命中。</p>
     *
     * <p><b>与 PDF 的差异：</b>DOCX 的脚注有明确对象（{@code XWPFFootnote}），
     * 可直接按编号取出内容；PDF 中脚注只是页面底部的普通文本，无法与页脚区分，
     * 因此 PDF 侧暂不支持脚注识别。</p>
     */
    private List<DocumentElement> extractFootnotes(XWPFDocument document) {
        List<DocumentElement> result = new ArrayList<>();
        try {
            for (XWPFFootnote footnote : document.getFootnotes()) {
                if (footnote == null) {
                    continue;
                }
                StringBuilder sb = new StringBuilder();
                for (XWPFParagraph paragraph : footnote.getParagraphs()) {
                    String text = paragraph == null ? null : paragraph.getText();
                    if (text != null && !text.isBlank()) {
                        if (sb.length() > 0) {
                            sb.append(" ");
                        }
                        sb.append(text.trim());
                    }
                }
                for (XWPFTable table : footnote.getTables()) {
                    String tableText = headerFooterTableText(table);
                    if (!tableText.isBlank()) {
                        if (sb.length() > 0) {
                            sb.append("\n");
                        }
                        sb.append(tableText);
                    }
                }
                String content = sb.toString().trim();
                if (content.isEmpty()) {
                    continue;
                }
                result.add(DocumentElement.builder()
                        .elementType(ElementType.FOOTNOTE)
                        .pageNo(0)
                        .content(content)
                        .processingMethod(ProcessingMethod.DIRECT_EXTRACT)
                        .confidence(1.0)
                        .remark("脚注编号: " + footnote.getId())
                        .build());
            }
        } catch (Exception e) {
            log.debug("[WordExtractor] 脚注提取失败: {}", e.getMessage());
        }
        return result;
    }

    // ======================== 段落 ========================

    private DocumentElement extractParagraph(XWPFParagraph paragraph) {
        String text = paragraph.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        // 段落样式明确给出标题级别（如 "Heading1"），比 PDF 靠字号猜测可靠得多
        String style = paragraph.getStyle();
        boolean heading = style != null && style.toLowerCase().contains("heading");
        ElementType type = heading ? ElementType.HEADING : ElementType.TEXT;
        return DocumentElement.builder()
                .elementType(type)
                .pageNo(0)
                .content(text.trim())
                .processingMethod(ProcessingMethod.DIRECT_EXTRACT)
                .confidence(1.0)
                .remark(style != null ? "样式: " + style : null)
                .build();
    }

    // ======================== 表格 ========================

    /**
     * 提取表格并保留行列结构。
     *
     * <p>POI 的表格模型天然包含行列关系，因此这里不会像纯文本抽取那样丢失结构。
     * 输出 Markdown 表格，便于后续分块与检索。</p>
     */
    private DocumentElement extractTable(XWPFTable table) {
        try {
            List<List<String>> rows = new ArrayList<>();
            for (XWPFTableRow row : table.getRows()) {
                List<String> cells = new ArrayList<>();
                for (XWPFTableCell cell : row.getTableCells()) {
                    cells.add(cell.getText() == null ? "" : cell.getText().trim().replace("\n", " "));
                }
                rows.add(cells);
            }
            if (rows.isEmpty()) {
                return null;
            }
            int columnCount = rows.stream().mapToInt(List::size).max().orElse(0);
            if (columnCount == 0) {
                return null;
            }
            // 补齐不等长的行（合并单元格会导致某行单元格数偏少）
            for (List<String> row : rows) {
                while (row.size() < columnCount) {
                    row.add("");
                }
            }
            List<String> header = new ArrayList<>();
            if (rows.size() > 1) {
                long nonEmpty = rows.get(0).stream().filter(c -> !c.isEmpty()).count();
                // 首行全非空时视为表头
                if (nonEmpty == columnCount) {
                    header = new ArrayList<>(rows.get(0));
                    rows = new ArrayList<>(rows.subList(1, rows.size()));
                }
            }
            boolean hasMerged = table.getRows().stream()
                    .flatMap(r -> r.getTableCells().stream())
                    .anyMatch(this::isMergedCell);
            return DocumentElement.builder()
                    .elementType(ElementType.TABLE)
                    .pageNo(0)
                    .content(toMarkdownTable(header, rows, columnCount))
                    .processingMethod(ProcessingMethod.TABLE_STRUCTURED)
                    .confidence(0.95)
                    .remark(String.format("%d行×%d列%s",
                            rows.size() + (header.isEmpty() ? 0 : 1),
                            columnCount, hasMerged ? " 含合并单元格" : ""))
                    .tableHeader(header)
                    .tableRows(rows)
                    .tableColumnCount(columnCount)
                    .build();
        } catch (Exception e) {
            log.debug("[WordExtractor] 表格提取失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 判断是否为合并单元格（跨列/跨行合并）。
     *
     * <p>合并单元格是复杂表格的标志：它意味着行列不再严格对齐，
     * 下游若要还原为二维表需要额外处理。这里仅做标记，不展开合并区域。</p>
     */
    private boolean isMergedCell(XWPFTableCell cell) {
        try {
            if (cell.getCTTc() == null || cell.getCTTc().getTcPr() == null) {
                return false;
            }
            var tcPr = cell.getCTTc().getTcPr();
            return tcPr.isSetGridSpan() || tcPr.isSetVMerge();
        } catch (Exception e) {
            return false;
        }
    }

    // ======================== 图片 ========================

    /**
     * 提取段落中的嵌入图片并交由视觉模型识别。
     */
    private List<DocumentElement> extractParagraphImages(XWPFParagraph paragraph) {
        List<DocumentElement> result = new ArrayList<>();
        if (!isVisionAvailable()) {
            return result;
        }
        for (IRunElement runElement : paragraph.getRuns()) {
            if (!(runElement instanceof XWPFRun run)) {
                continue;
            }
            for (XWPFPicture picture : run.getEmbeddedPictures()) {
                try {
                    XWPFPictureData pictureData = picture.getPictureData();
                    if (pictureData == null || pictureData.getData() == null) {
                        continue;
                    }
                    byte[] imageBytes = convertToPng(pictureData.getData());
                    if (imageBytes == null) {
                        continue;
                    }
                    if (picture.getCTPicture() == null) {
                        continue;
                    }
                    String description = visionProcessor.describe(imageBytes, "image/png");
                    if (description == null || description.isBlank()) {
                        continue;
                    }
                    ElementType type = containsChartKeyword(description)
                            ? ElementType.CHART : ElementType.IMAGE;
                    result.add(DocumentElement.builder()
                            .elementType(type)
                            .pageNo(0)
                            .content(description.trim())
                            .processingMethod(ProcessingMethod.VISION_MODEL)
                            .confidence(0.75)
                            .remark("嵌入图片 " + pictureData.getFileName())
                            .build());
                } catch (Exception e) {
                    log.debug("[WordExtractor] 图片处理失败: {}", e.getMessage());
                }
            }
        }
        return result;
    }

    /**
     * 把嵌入图片统一转为 PNG。
     *
     * <p>Word 中图片可能是 EMF/WMF 等矢量格式，视觉模型通常不支持，
     * 统一转 PNG 可提高兼容性（同时也统一了体积）。</p>
     */
    private byte[] convertToPng(byte[] originalBytes) {
        try {
            java.awt.image.BufferedImage image;
            try (InputStream in = new ByteArrayInputStream(originalBytes)) {
                image = ImageIO.read(in);
            }
            if (image == null) {
                return null;
            }
            if (image.getWidth() < MIN_IMAGE_SIZE || image.getHeight() < MIN_IMAGE_SIZE) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            log.debug("[WordExtractor] 图片格式转换失败: {}", e.getMessage());
            return null;
        }
    }

    private boolean containsChartKeyword(String description) {
        if (description == null) {
            return false;
        }
        String lower = description.toLowerCase();
        return lower.contains("图表") || lower.contains("柱状图") || lower.contains("折线图")
                || lower.contains("饼图") || lower.contains("chart") || lower.contains("graph");
    }

    private boolean isVisionAvailable() {
        return properties.isVisionEnabled() && visionProcessor != null && visionProcessor.isAvailable();
    }

    // ======================== 辅助 ========================

    private String toMarkdownTable(List<String> header, List<List<String>> rows, int columnCount) {
        StringBuilder sb = new StringBuilder();
        if (!header.isEmpty()) {
            sb.append("| ").append(String.join(" | ", header)).append(" |\n|");
            for (int i = 0; i < columnCount; i++) {
                sb.append(" --- |");
            }
            sb.append("\n");
        }
        for (List<String> row : rows) {
            sb.append("| ").append(String.join(" | ", row)).append(" |\n");
        }
        return sb.toString().trim();
    }

    private void reindex(List<DocumentElement> elements) {
        for (int i = 0; i < elements.size(); i++) {
            elements.get(i).setOrderIndex(i);
        }
    }
}
