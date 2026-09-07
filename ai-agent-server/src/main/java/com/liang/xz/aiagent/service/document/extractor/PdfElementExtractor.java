package com.liang.xz.aiagent.service.document.extractor;

import com.liang.xz.aiagent.service.document.model.DocumentElement;
import com.liang.xz.aiagent.service.document.model.DocumentElement.ElementType;
import com.liang.xz.aiagent.service.document.model.DocumentElement.ProcessingMethod;
import com.liang.xz.aiagent.service.document.model.DocumentAnalysisProperties;
import com.liang.xz.aiagent.service.document.model.VisionProcessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>PDF 元素提取器 —— 基于 PDFBox 的深度内容分析</p>
 *
 * <p><b>相比原有 Tika 纯文本抽取，新增四类能力：</b></p>
 * <ol>
 *   <li><b>扫描件识别：</b>页面文本层为空（或极少）却存在大尺寸图片时，判定为图像型页面，
 *       渲染成图片后交由视觉模型识别，而不是像原来那样返回空内容。</li>
 *   <li><b>表格结构还原：</b>用文本坐标做行聚类 + 列间隙检测，还原行列关系并输出 Markdown 表格，
 *       而非退化为按单元格顺序拼接的裸文本。</li>
 *   <li><b>页眉页脚分离：</b>按纵向位置 + 跨页重复度识别，单独标记，不混入正文。</li>
 *   <li><b>嵌入图片提取：</b>抽取页面内嵌图片，按需调用视觉模型生成描述。</li>
 * </ol>
 *
 * <p><b>已知局限（务实取舍）：</b>表格检测采用坐标聚类启发式，对"有边框或列对齐明显"的表格效果较好；
 * 对跨页表格、嵌套表格、极度不规则的布局识别率有限。若业务强依赖复杂表格抽取，
 * 可替换为 Tabula / Camelot 等专用库，本类的产出结构无需改变。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
public class PdfElementExtractor {

    /** 判定为扫描页的文本字符数阈值：低于该值且有图片覆盖，则认为是图像型页面 */
    private static final int SCANNED_TEXT_THRESHOLD = 20;

    /** 判定"大图"的最小面积占比（相对于页面面积），用于识别扫描件底图 */
    private static final float LARGE_IMAGE_RATIO = 0.25f;

    /** 渲染扫描页的 DPI：兼顾识别率与接口体积，过高会让图片超出视觉模型限制 */
    private static final int RENDER_DPI = 150;

    /** 行聚类容差（pt）：纵向坐标差小于该值视为同一行 */
    private static final float ROW_TOLERANCE = 3.0f;

    /** 列分隔判定的最小水平间隙（pt）：超过该值认为存在列边界 */
    private static final float COLUMN_GAP_THRESHOLD = 12.0f;

    /** 构成表格的最少行数 */
    private static final int MIN_TABLE_ROWS = 2;

    /** 构成表格的最少列数 */
    private static final int MIN_TABLE_COLUMNS = 2;

    /** 页眉区域：页面顶部该比例以内 */
    private static final float HEADER_ZONE_RATIO = 0.08f;

    /** 页脚区域：页面底部该比例以内 */
    private static final float FOOTER_ZONE_RATIO = 0.08f;

    private final DocumentAnalysisProperties properties;
    private final VisionProcessor visionProcessor;

    public PdfElementExtractor(DocumentAnalysisProperties properties, VisionProcessor visionProcessor) {
        this.properties = properties;
        this.visionProcessor = visionProcessor;
    }

    /**
     * 提取 PDF 中的全部元素。
     *
     * @param pdfData PDF 文件字节
     * @return 元素列表（按页码与纵向位置排序）；失败返回空列表
     */
    public List<DocumentElement> extract(byte[] pdfData) {
        List<DocumentElement> elements = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdfData)) {
            int pageCount = document.getNumberOfPages();
            // 先收集各页文本片段（含坐标），供页眉页脚的"跨页重复"判定使用
            Map<Integer, List<TextFragment>> pageFragments = new LinkedHashMap<>();

            for (int i = 0; i < pageCount; i++) {
                PDPage page = document.getPage(i);
                int pageNo = i + 1;
                PDRectangle pageSize = page.getMediaBox();
                float pageWidth = pageSize.getWidth();
                float pageHeight = pageSize.getHeight();

                List<TextFragment> fragments = collectTextFragments(document, i);
                pageFragments.put(pageNo, fragments);

                int pageTextLength = fragments.stream()
                        .mapToInt(f -> f.text == null ? 0 : f.text.length()).sum();
                List<PDImageXObject> images = collectImages(page);

                // 1. 扫描页判定：文本极少 + 存在大图 → 整页走视觉识别
                boolean scanned = pageTextLength < SCANNED_TEXT_THRESHOLD
                        && hasLargeImage(images, page, pageWidth, pageHeight);
                if (scanned && isVisionAvailable()) {
                    DocumentElement scannedElement = extractScannedPage(document, i, pageNo);
                    if (scannedElement != null) {
                        elements.add(scannedElement);
                        continue; // 扫描页无需再做文本层分析
                    }
                }

                // 2. 页眉页脚：按纵向区域提取，单独标记
                elements.addAll(extractHeaderFooter(fragments, pageNo, pageHeight));

                // 3. 正文：表格优先结构化还原，其余作为文本段落
                elements.addAll(extractBodyElements(fragments, pageNo, pageHeight));

                // 4. 嵌入图片（排除已作为扫描底图处理的情况）
                if (!scanned) {
                    elements.addAll(extractImages(images, pageNo, pageWidth, pageHeight));
                }

                // 5. 批注：PDF 的批注有明确对象模型，可直接读取（审阅意见、高亮备注等）
                elements.addAll(extractAnnotations(page, pageNo));
            }
            elements.sort(Comparator
                    .comparingInt(DocumentElement::getPageNo)
                    .thenComparingDouble(PdfElementExtractor::topOf));
            reindex(elements);
            log.debug("[PdfExtractor] 页数={}, 提取元素={}", pageCount, elements.size());
        } catch (Exception e) {
            log.warn("[PdfExtractor] PDF 解析失败，将回退到纯文本抽取: {}", e.getMessage());
        }
        return elements;
    }

    // ======================== 文本收集 ========================

    /**
     * 收集页面的文本片段（含坐标）。
     */
    private List<TextFragment> collectTextFragments(PDDocument document, int pageIndex) throws IOException {
        CollectingStripper stripper = new CollectingStripper();
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        stripper.setSortByPosition(true);
        stripper.getText(document);
        return stripper.getFragments();
    }

    // ======================== 页眉页脚 ========================

    /**
     * 按纵向位置识别页眉页脚。
     *
     * <p>PDF 没有页眉页脚的语义标记，只能用位置启发式：位于页面顶部/底部固定比例区域内
     * 的文本块视为页眉页脚。这样处理的目的不是"完美识别"，而是<b>把结构性噪声与正文分离</b>，
     * 避免"公司名称""第 N 页"这类内容污染 RAG 检索。</p>
     */
    private List<DocumentElement> extractHeaderFooter(List<TextFragment> fragments,
                                                      int pageNo, float pageHeight) {
        List<DocumentElement> result = new ArrayList<>();
        if (fragments == null || fragments.isEmpty()) {
            return result;
        }
        float headerBottom = pageHeight * HEADER_ZONE_RATIO;
        float footerTop = pageHeight * (1 - FOOTER_ZONE_RATIO);

        List<TextFragment> headerParts = new ArrayList<>();
        List<TextFragment> footerParts = new ArrayList<>();
        for (TextFragment fragment : fragments) {
            if (fragment.yTop <= headerBottom) {
                headerParts.add(fragment);
            } else if (fragment.yBottom >= footerTop) {
                footerParts.add(fragment);
            }
        }
        if (!headerParts.isEmpty()) {
            result.add(buildZoneElement(headerParts, ElementType.HEADER, pageNo, pageHeight,
                    "页面顶部 " + (int) (HEADER_ZONE_RATIO * 100) + "% 区域"));
        }
        if (!footerParts.isEmpty()) {
            result.add(buildZoneElement(footerParts, ElementType.FOOTER, pageNo, pageHeight,
                    "页面底部 " + (int) (FOOTER_ZONE_RATIO * 100) + "% 区域"));
        }
        return result;
    }

    private DocumentElement buildZoneElement(List<TextFragment> parts, ElementType type,
                                             int pageNo, float pageHeight, String note) {
        parts.sort(Comparator.comparingDouble(f -> f.xLeft));
        StringBuilder text = new StringBuilder();
        for (TextFragment part : parts) {
            if (text.length() > 0) {
                text.append(" ");
            }
            text.append(part.text);
        }
        float minX = parts.stream().map(f -> f.xLeft).min(Float::compareTo).orElse(0f);
        float maxX = parts.stream().map(f -> f.xRight).max(Float::compareTo).orElse(0f);
        float minY = parts.stream().map(f -> f.yTop).min(Float::compareTo).orElse(0f);
        float maxY = parts.stream().map(f -> f.yBottom).max(Float::compareTo).orElse(0f);
        return DocumentElement.builder()
                .elementType(type)
                .pageNo(pageNo)
                .bbox(new float[]{minX / pageHeight, minY / pageHeight, maxX / pageHeight, maxY / pageHeight})
                .content(text.toString().trim())
                .processingMethod(ProcessingMethod.RULE_INFERRED)
                .confidence(0.7)
                .remark(note)
                .build();
    }

    // ======================== 正文（表格 + 段落） ========================

    /**
     * 提取正文元素：先按行聚类，再识别表格区域，其余作为文本段落。
     */
    private List<DocumentElement> extractBodyElements(List<TextFragment> fragments,
                                                      int pageNo, float pageHeight) {
        List<DocumentElement> result = new ArrayList<>();
        if (fragments == null || fragments.isEmpty()) {
            return result;
        }
        // 排除已归入页眉页脚的部分
        float headerBottom = pageHeight * HEADER_ZONE_RATIO;
        float footerTop = pageHeight * (1 - FOOTER_ZONE_RATIO);
        List<TextFragment> bodyFragments = new ArrayList<>();
        for (TextFragment fragment : fragments) {
            if (fragment.yTop > headerBottom && fragment.yBottom < footerTop) {
                bodyFragments.add(fragment);
            }
        }
        if (bodyFragments.isEmpty()) {
            return result;
        }

        List<TextRow> rows = clusterIntoRows(bodyFragments);
        int index = 0;
        while (index < rows.size()) {
            TextRow row = rows.get(index);
            if (row.cells.size() >= MIN_TABLE_COLUMNS) {
                // 从当前行开始，收集连续的多列行构成表格
                List<TextRow> tableRows = new ArrayList<>();
                int cursor = index;
                while (cursor < rows.size() && rows.get(cursor).cells.size() >= MIN_TABLE_COLUMNS) {
                    tableRows.add(rows.get(cursor));
                    cursor++;
                }
                if (tableRows.size() >= MIN_TABLE_ROWS) {
                    result.add(buildTableElement(tableRows, pageNo, pageHeight));
                    index = cursor;
                    continue;
                }
            }
            result.add(buildTextElement(row, pageNo, pageHeight));
            index++;
        }
        return result;
    }

    /**
     * 把文本片段按纵向坐标聚类成行，并在行内按水平间隙切分为单元格。
     */
    private List<TextRow> clusterIntoRows(List<TextFragment> fragments) {
        List<TextFragment> sorted = new ArrayList<>(fragments);
        sorted.sort(Comparator.comparingDouble(f -> f.yTop));

        List<TextRow> rows = new ArrayList<>();
        TextRow currentRow = null;
        for (TextFragment fragment : sorted) {
            if (currentRow == null || Math.abs(fragment.yTop - currentRow.yTop) > ROW_TOLERANCE) {
                currentRow = new TextRow(fragment.yTop);
                rows.add(currentRow);
            }
            currentRow.fragments.add(fragment);
        }
        // 行内按 X 排序并依据间隙切分单元格
        for (TextRow row : rows) {
            row.fragments.sort(Comparator.comparingDouble(f -> f.xLeft));
            List<String> cells = new ArrayList<>();
            StringBuilder cell = new StringBuilder();
            Float previousRight = null;
            for (TextFragment fragment : row.fragments) {
                boolean newColumn = previousRight != null
                        && (fragment.xLeft - previousRight) > COLUMN_GAP_THRESHOLD;
                if (newColumn) {
                    cells.add(cell.toString().trim());
                    cell.setLength(0);
                }
                if (cell.length() > 0) {
                    cell.append(' ');
                }
                cell.append(fragment.text);
                previousRight = fragment.xRight;
            }
            if (cell.length() > 0) {
                cells.add(cell.toString().trim());
            }
            row.cells = cells;
        }
        return rows;
    }

    private DocumentElement buildTextElement(TextRow row, int pageNo, float pageHeight) {
        String text = String.join(" ", row.cells).trim();
        float minY = row.fragments.stream().map(f -> f.yTop).min(Float::compareTo).orElse(0f);
        float maxY = row.fragments.stream().map(f -> f.yBottom).max(Float::compareTo).orElse(0f);
        float minX = row.fragments.stream().map(f -> f.xLeft).min(Float::compareTo).orElse(0f);
        float maxX = row.fragments.stream().map(f -> f.xRight).max(Float::compareTo).orElse(0f);
        // 字号显著偏大且文本较短 → 判为标题（PDF 无样式信息，只能用字号启发式）
        boolean heading = row.fragments.size() <= 3 && text.length() <= 60
                && row.fragments.stream().anyMatch(f -> f.fontSize >= 14f);
        return DocumentElement.builder()
                .elementType(heading ? ElementType.HEADING : ElementType.TEXT)
                .pageNo(pageNo)
                .bbox(new float[]{minX / pageHeight, minY / pageHeight, maxX / pageHeight, maxY / pageHeight})
                .content(text)
                .processingMethod(ProcessingMethod.DIRECT_EXTRACT)
                .confidence(1.0)
                .build();
    }

    private DocumentElement buildTableElement(List<TextRow> rows, int pageNo, float pageHeight) {
        int columnCount = rows.stream().mapToInt(r -> r.cells.size()).max().orElse(0);
        List<List<String>> dataRows = new ArrayList<>();
        for (TextRow row : rows) {
            List<String> cells = new ArrayList<>(row.cells);
            while (cells.size() < columnCount) {
                cells.add("");
            }
            dataRows.add(cells);
        }
        // 首行作为表头（若其非空单元格数与列数一致，认为是表头行）
        List<String> header = new ArrayList<>();
        if (!dataRows.isEmpty()) {
            long nonEmpty = dataRows.get(0).stream().filter(c -> !c.isEmpty()).count();
            if (nonEmpty == columnCount) {
                header = new ArrayList<>(dataRows.get(0));
                dataRows = new ArrayList<>(dataRows.subList(1, dataRows.size()));
            }
        }

        float minY = rows.stream().flatMap(r -> r.fragments.stream())
                .map(f -> f.yTop).min(Float::compareTo).orElse(0f);
        float maxY = rows.stream().flatMap(r -> r.fragments.stream())
                .map(f -> f.yBottom).max(Float::compareTo).orElse(0f);

        return DocumentElement.builder()
                .elementType(ElementType.TABLE)
                .pageNo(pageNo)
                .bbox(new float[]{0f, minY / pageHeight, 1f, maxY / pageHeight})
                .content(toMarkdownTable(header, dataRows, columnCount))
                .processingMethod(ProcessingMethod.TABLE_STRUCTURED)
                .confidence(0.8)
                .remark(String.format("%d行×%d列", dataRows.size() + (header.isEmpty() ? 0 : 1), columnCount))
                .tableHeader(header)
                .tableRows(dataRows)
                .tableColumnCount(columnCount)
                .build();
    }

    /**
     * 将表格数据转为 Markdown 串。
     *
     * <p>选择 Markdown 而非 JSON 的原因：分块入库后作为文本检索时，Markdown 表格
     * 对人（最终答案生成）和向量模型都更友好，且体积小。</p>
     */
    private String toMarkdownTable(List<String> header, List<List<String>> rows, int columnCount) {
        StringBuilder sb = new StringBuilder();
        if (!header.isEmpty()) {
            sb.append("| ").append(String.join(" | ", header)).append(" |\n");
            sb.append("|");
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

    // ======================== 图片与扫描页 ========================

    private List<PDImageXObject> collectImages(PDPage page) {
        List<PDImageXObject> images = new ArrayList<>();
        try {
            PDResources resources = page.getResources();
            if (resources == null) {
                return images;
            }
            for (COSName name : resources.getXObjectNames()) {
                PDXObject xObject = resources.getXObject(name);
                if (xObject instanceof PDImageXObject image) {
                    images.add(image);
                }
            }
        } catch (IOException e) {
            log.debug("[PdfExtractor] 读取页面图片资源失败: {}", e.getMessage());
        }
        return images;
    }

    private boolean hasLargeImage(List<PDImageXObject> images, PDPage page,
                                 float pageWidth, float pageHeight) {
        if (images.isEmpty() || pageWidth <= 0 || pageHeight <= 0) {
            return false;
        }
        float pageArea = pageWidth * pageHeight;
        for (PDImageXObject image : images) {
            float imageArea = (float) image.getWidth() * (float) image.getHeight();
            if (imageArea / pageArea >= LARGE_IMAGE_RATIO) {
                return true;
            }
        }
        return false;
    }

    /**
     * 处理扫描页：渲染为 PNG 后交视觉模型识别。
     */
    private DocumentElement extractScannedPage(PDDocument document, int pageIndex, int pageNo) {
        try {
            byte[] pngBytes = renderPageToPng(document, pageIndex);
            if (pngBytes == null) {
                return null;
            }
            String description = visionProcessor.describe(pngBytes, "image/png");
            if (description == null || description.isBlank()) {
                return DocumentElement.builder()
                        .elementType(ElementType.SCANNED_PAGE)
                        .pageNo(pageNo)
                        .content("")
                        .processingMethod(ProcessingMethod.SKIPPED)
                        .confidence(0.0)
                        .remark("视觉模型未返回内容")
                        .build();
            }
            return DocumentElement.builder()
                    .elementType(ElementType.SCANNED_PAGE)
                    .pageNo(pageNo)
                    .content(description.trim())
                    .processingMethod(ProcessingMethod.VISION_MODEL)
                    // 视觉识别结果的可信度低于文本层直接提取，给 0.75 以便下游判断
                    .confidence(0.75)
                    .remark("扫描页经视觉模型识别（渲染DPI=" + RENDER_DPI + "）")
                    .build();
        } catch (Exception e) {
            log.warn("[PdfExtractor] 扫描页识别失败 page={}: {}", pageNo, e.getMessage());
            return null;
        }
    }

    private List<DocumentElement> extractImages(List<PDImageXObject> images, int pageNo,
                                                float pageWidth, float pageHeight) {
        List<DocumentElement> result = new ArrayList<>();
        if (images.isEmpty() || !isVisionAvailable()) {
            return result;
        }
        int limit = properties.getMaxImagesPerDocument();
        for (int i = 0; i < images.size() && result.size() < limit; i++) {
            PDImageXObject image = images.get(i);
            // 过小的图片多为装饰性图标/分隔线，识别成本高而信息量低
            if (image.getWidth() < 40 || image.getHeight() < 40) {
                continue;
            }
            try {
                byte[] pngBytes = toPng(image.getImage());
                String description = visionProcessor.describe(pngBytes, "image/png");
                if (description == null || description.isBlank()) {
                    continue;
                }
                ElementType type = looksLikeChart(description) ? ElementType.CHART : ElementType.IMAGE;
                result.add(DocumentElement.builder()
                        .elementType(type)
                        .pageNo(pageNo)
                        .content(description.trim())
                        .processingMethod(ProcessingMethod.VISION_MODEL)
                        .confidence(0.75)
                        .remark(String.format("图片 %dx%d px", image.getWidth(), image.getHeight()))
                        .build());
            } catch (Exception e) {
                log.debug("[PdfExtractor] 图片识别失败 page={} index={}: {}", pageNo, i, e.getMessage());
            }
        }
        return result;
    }

    private byte[] renderPageToPng(PDDocument document, int pageIndex) throws IOException {
        PDFRenderer renderer = new PDFRenderer(document);
        BufferedImage image = renderer.renderImageWithDPI(pageIndex, RENDER_DPI);
        return toPng(image);
    }

    private byte[] toPng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /**
     * 依据视觉模型描述粗略判断是否为图表。
     *
     * <p>这是启发式判断而非精确分类：借助模型描述中的关键词区分"普通图片"与"数据图表"，
     * 因为图表信息密度高，下游往往需要保留完整结构而非当作插图。</p>
     */
    private boolean looksLikeChart(String description) {
        if (description == null) {
            return false;
        }
        String lower = description.toLowerCase();
        return lower.contains("柱状图") || lower.contains("折线图") || lower.contains("饼图")
                || lower.contains("图表") || lower.contains("统计图") || lower.contains("趋势图")
                || lower.contains("chart") || lower.contains("graph") || lower.contains("diagram")
                || lower.contains("柱状") || lower.contains("曲线");
    }

    /**
     * 提取页面批注。
     *
     * <p>PDF 的批注（高亮、批注框、下划线等）在对象模型中有明确定义（{@code PDAnnotation}），
     * 可直接读取内容与作者。批注不属于正文，因此单独记录，供需要时查看审阅意见。</p>
     */
    private List<DocumentElement> extractAnnotations(PDPage page, int pageNo) {
        List<DocumentElement> result = new ArrayList<>();
        try {
            List<PDAnnotation> annotations = page.getAnnotations();
            if (annotations == null || annotations.isEmpty()) {
                return result;
            }
            for (PDAnnotation annotation : annotations) {
                String contents = annotation.getContents();
                if (contents == null || contents.isBlank()) {
                    continue;
                }
                // PDFBox 2.0 的 PDAnnotation 未提供 getRect()，需从 COS 字典取 Rect 数组
                PDRectangle rect = null;
                COSBase rectBase = annotation.getCOSObject().getDictionaryObject(COSName.RECT);
                if (rectBase instanceof COSArray rectArray) {
                    rect = new PDRectangle(rectArray);
                }
                float[] bbox = null;
                if (rect != null) {
                    PDRectangle pageSize = page.getMediaBox();
                    float height = pageSize != null && pageSize.getHeight() > 0
                            ? pageSize.getHeight() : 1f;
                    bbox = new float[]{
                            rect.getLowerLeftX() / height, rect.getLowerLeftY() / height,
                            rect.getUpperRightX() / height, rect.getUpperRightY() / height};
                }
                result.add(DocumentElement.builder()
                        .elementType(ElementType.ANNOTATION)
                        .pageNo(pageNo)
                        .bbox(bbox)
                        .content(contents.trim())
                        .processingMethod(ProcessingMethod.DIRECT_EXTRACT)
                        .confidence(1.0)
                        .remark("批注类型: " + annotation.getSubtype())
                        .build());
            }
        } catch (Exception e) {
            log.debug("[PdfExtractor] 批注提取失败 page={}: {}", pageNo, e.getMessage());
        }
        return result;
    }

    private boolean isVisionAvailable() {
        return properties.isVisionEnabled() && visionProcessor != null && visionProcessor.isAvailable();
    }

    // ======================== 辅助 ========================

    private void reindex(List<DocumentElement> elements) {
        for (int i = 0; i < elements.size(); i++) {
            elements.get(i).setOrderIndex(i);
        }
    }

    private static double topOf(DocumentElement element) {
        float[] bbox = element.getBbox();
        return bbox != null && bbox.length >= 2 ? bbox[1] : Double.MAX_VALUE;
    }

    /**
     * 带坐标的文本片段。
     */
    private static class TextFragment {
        private final String text;
        private final float xLeft;
        private final float xRight;
        private final float yTop;
        private final float yBottom;
        private final float fontSize;

        TextFragment(String text, float xLeft, float xRight, float yTop, float yBottom, float fontSize) {
            this.text = text;
            this.xLeft = xLeft;
            this.xRight = xRight;
            this.yTop = yTop;
            this.yBottom = yBottom;
            this.fontSize = fontSize;
        }
    }

    /**
     * 聚类后的一行文本（含切分出的单元格）。
     */
    private static class TextRow {
        private final float yTop;
        private final List<TextFragment> fragments = new ArrayList<>();
        private List<String> cells = new ArrayList<>();

        TextRow(float yTop) {
            this.yTop = yTop;
        }
    }

    /**
     * 自定义 TextStripper：保留每个文本片段的坐标，供表格与页眉页脚判定使用。
     *
     * <p>PDFBox 默认的 {@code PDFTextStripper} 只输出拼接后的字符串，坐标信息会丢失；
     * 重写 {@code writeString} 才能在抽取文本的同时拿到布局数据。</p>
     */
    private static class CollectingStripper extends PDFTextStripper {
        private final List<TextFragment> fragments = new ArrayList<>();

        CollectingStripper() throws IOException {
            super();
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) {
            if (text == null || text.trim().isEmpty() || textPositions == null || textPositions.isEmpty()) {
                return;
            }
            float xLeft = Float.MAX_VALUE;
            float xRight = Float.MIN_VALUE;
            float yTop = Float.MAX_VALUE;
            float yBottom = Float.MIN_VALUE;
            float maxFontSize = 0f;
            for (TextPosition position : textPositions) {
                xLeft = Math.min(xLeft, position.getX());
                xRight = Math.max(xRight, position.getX() + position.getWidth());
                yTop = Math.min(yTop, position.getY());
                yBottom = Math.max(yBottom, position.getY() + position.getHeight());
                maxFontSize = Math.max(maxFontSize, position.getFontSize());
            }
            fragments.add(new TextFragment(text.trim(), xLeft, xRight, yTop, yBottom, maxFontSize));
        }

        List<TextFragment> getFragments() {
            return fragments;
        }
    }
}
