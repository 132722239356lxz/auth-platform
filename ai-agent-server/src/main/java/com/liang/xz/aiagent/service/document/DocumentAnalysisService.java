package com.liang.xz.aiagent.service.document;

import com.liang.xz.aiagent.config.AiProperties;
import com.liang.xz.aiagent.llm.LlmClient;
import com.liang.xz.aiagent.service.document.extractor.PdfElementExtractor;
import com.liang.xz.aiagent.service.document.extractor.WordElementExtractor;
import com.liang.xz.aiagent.service.document.model.DocumentAnalysisProperties;
import com.liang.xz.aiagent.service.document.model.DocumentAnalysisResult;
import com.liang.xz.aiagent.service.document.model.DocumentElement;
import com.liang.xz.aiagent.service.document.model.DocumentElement.ElementType;
import com.liang.xz.aiagent.service.document.model.VisionProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>文档深度分析服务 —— 编排"检测 → 分类 → 按需调用模型 → 汇总元数据"的完整流程</p>
 *
 * <p><b>解决的问题：</b>原有 {@code FileParserService} 用 Tika 把整篇文档压成纯文本，
 * 导致三类信息永久丢失：内嵌图片、表格的行列关系、页眉页脚与正文的界限。
 * 本服务把文档还原为带类型与位置的元素序列，并对无法用规则提取的内容按需调用模型。</p>
 *
 * <p><b>模型调用策略（该用才用）：</b></p>
 * <pre>
 *   纯文本段落   → 规则直接提取，不调用模型（零成本）
 *   表格         → 结构还原，不调用模型（行列关系来自坐标/对象模型）
 *   嵌入图片     → 调用视觉模型（否则图片信息完全丢失）
 *   扫描件页面   → 渲染后调用视觉模型（文本层为空，只能靠视觉识别）
 *   页眉页脚     → 规则识别，不调用模型
 * </pre>
 * <p>视觉模型调用受 {@code maxImagesPerDocument} 与 {@code visionEnabled} 双重约束，
 * 避免大文档产生失控的调用成本。</p>
 *
 * <p><b>降级原则：</b>深度分析失败（PDF 加密、格式异常、超时）时返回空元素列表，
 * 由调用方回退到原有纯文本抽取，绝不阻塞文件上传主流程。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class DocumentAnalysisService {

    private final DocumentAnalysisProperties properties;
    private final PdfElementExtractor pdfExtractor;
    private final WordElementExtractor wordExtractor;

    /** 视觉模型调用计数（当次分析内共享），用于写入结果元数据 */
    private final AtomicInteger visionCallCounter = new AtomicInteger(0);

    public DocumentAnalysisService(AiProperties aiProperties, LlmClient llmClient) {
        this.properties = aiProperties.getDocumentAnalysis();
        VisionProcessor visionProcessor = createVisionProcessor(llmClient);
        this.pdfExtractor = new PdfElementExtractor(properties, visionProcessor);
        this.wordExtractor = new WordElementExtractor(properties, visionProcessor);
    }

    /**
     * 分析文档，返回带完整元数据的结果。
     *
     * @param fileData 文件字节
     * @param fileName 文件名（用于判定格式）
     * @return 分析结果；未启用或格式不支持时返回 null，由调用方回退纯文本抽取
     */
    public DocumentAnalysisResult analyze(byte[] fileData, String fileName) {
        if (!properties.isEnabled()) {
            log.debug("[DocAnalysis] 深度分析未启用，跳过: {}", fileName);
            return null;
        }
        if (fileData == null || fileData.length == 0) {
            return null;
        }
        String format = detectFormat(fileName);
        if ("OTHER".equals(format)) {
            // 非 PDF/Word 文档沿用原有 Tika 抽取路径
            return null;
        }

        long start = System.currentTimeMillis();
        visionCallCounter.set(0);
        try {
            List<DocumentElement> elements = "PDF".equals(format)
                    ? pdfExtractor.extract(fileData)
                    : wordExtractor.extract(fileData);

            if (elements.isEmpty()) {
                log.info("[DocAnalysis] 未提取到任何元素，建议回退纯文本抽取: {}", fileName);
                return null;
            }
            int pageCount = elements.stream().mapToInt(DocumentElement::getPageNo).max().orElse(0);
            String bodyText = buildBodyText(elements);
            int visionCalls = visionCallCounter.get();

            DocumentAnalysisResult result = DocumentAnalysisResult.builder()
                    .fileName(fileName)
                    .documentFormat(format)
                    .elements(elements)
                    .bodyText(bodyText)
                    .pageCount(pageCount)
                    .elapsedMs(System.currentTimeMillis() - start)
                    .visionModelUsed(visionCalls > 0)
                    .visionModelCallCount(visionCalls)
                    .degradationNote(buildDegradationNote(elements, visionCalls))
                    .build();
            log.info("[DocAnalysis] {}", result.toSummary());
            return result;
        } catch (Exception e) {
            log.warn("[DocAnalysis] 文档深度分析失败，将回退纯文本抽取: {} - {}", fileName, e.getMessage());
            return null;
        }
    }

    /**
     * 构建正文文本：仅拼接正文元素，并跳过空内容。
     *
     * <p><b>为什么排除页眉页脚：</b>页眉通常是公司名/文档标题，页脚是页码，
     * 它们在每个分块中重复出现会稀释向量表征，导致检索"命中页眉而非正文"。
     * 单独存储（元素表）可在需要时取用，但不污染正文。</p>
     */
    private String buildBodyText(List<DocumentElement> elements) {
        StringBuilder sb = new StringBuilder();
        for (DocumentElement element : elements) {
            if (!element.isBodyContent()) {
                continue;
            }
            String content = element.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            // 表格前加类型标注，让阅读者（含后续处理的大模型）知道这是结构化数据
            if (element.getElementType() == ElementType.TABLE) {
                sb.append("[表格] ");
            } else if (element.getElementType() == ElementType.IMAGE
                    || element.getElementType() == ElementType.CHART) {
                sb.append("[图片内容] ");
            } else if (element.getElementType() == ElementType.SCANNED_PAGE) {
                sb.append("[扫描页识别] ");
            }
            sb.append(content.trim());
        }
        return sb.toString();
    }

    /**
     * 生成降级说明：当部分能力未生效时记录原因，便于排障与结果解读。
     */
    private String buildDegradationNote(List<DocumentElement> elements, int visionCalls) {
        List<String> notes = new ArrayList<>();
        long imageElements = elements.stream()
                .filter(e -> e.getElementType() == ElementType.IMAGE
                        || e.getElementType() == ElementType.CHART
                        || e.getElementType() == ElementType.SCANNED_PAGE)
                .count();
        if (!properties.isVisionEnabled()) {
            notes.add("视觉模型未启用，图片/扫描件内容未识别");
        } else if (imageElements > 0 && visionCalls == 0) {
            notes.add("存在图片元素但视觉模型不可用，内容为空");
        }
        long skipped = elements.stream()
                .filter(e -> e.getProcessingMethod() == DocumentElement.ProcessingMethod.SKIPPED)
                .count();
        if (skipped > 0) {
            notes.add(skipped + " 个元素因超出处理上限或识别失败被跳过");
        }
        return notes.isEmpty() ? null : String.join("；", notes);
    }

    /**
     * 构造视觉处理器：包装 LlmClient 的视觉能力，并统计调用次数。
     */
    private VisionProcessor createVisionProcessor(LlmClient llmClient) {
        if (llmClient == null) {
            return new VisionProcessor() {
                @Override
                public String describe(byte[] imageBytes, String mimeType) {
                    return null;
                }

                @Override
                public boolean isAvailable() {
                    return false;
                }
            };
        }
        return new VisionProcessor() {
            @Override
            public String describe(byte[] imageBytes, String mimeType) {
                if (!properties.isVisionEnabled()) {
                    return null;
                }
                // 视觉调用计数，供结果元数据统计使用
                visionCallCounter.incrementAndGet();
                try {
                    return llmClient.describeImage(imageBytes, mimeType);
                } catch (Exception e) {
                    log.warn("[DocAnalysis] 视觉模型调用失败: {}", e.getMessage());
                    return null;
                }
            }

            /**
             * 说明：LlmClient 未提供"视觉能力是否可用"的探测接口，而供应商是否支持多模态
             * 只有在真正调用时才能确定。因此这里只依据配置开关判断，调用失败由
             * {@code describe} 内部捕获并记录为空内容，元素仍会被标记为 SKIPPED——
             * 这样即使视觉模型不可用，文档分析也不会中断，只是图片内容缺失。
             */
            @Override
            public boolean isAvailable() {
                return properties.isVisionEnabled();
            }
        };
    }

    /**
     * 依据扩展名判定文档格式。
     */
    private String detectFormat(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "PDF";
        }
        if (lower.endsWith(".docx") || lower.endsWith(".doc")) {
            return "DOCX";
        }
        return "OTHER";
    }

    /**
     * 判断该文件是否适用深度分析（供调用方决定是否走本服务）。
     */
    public boolean supports(String fileName) {
        if (!properties.isEnabled()) {
            return false;
        }
        return !"OTHER".equals(detectFormat(fileName));
    }
}
