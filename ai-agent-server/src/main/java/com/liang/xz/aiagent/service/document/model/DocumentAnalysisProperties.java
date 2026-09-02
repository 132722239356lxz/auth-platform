package com.liang.xz.aiagent.service.document.model;

import lombok.Data;

/**
 * <p>文档深度分析配置</p>
 *
 * <p><b>为什么需要这些开关：</b>深度分析（尤其是视觉模型识别）比纯文本抽取昂贵得多——
 * 一份 50 页的扫描件 PDF 若逐页调用视觉模型，会产生 50 次模型调用。
 * 因此需要开关与上限，让不同环境按成本预算选择分析深度。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
public class DocumentAnalysisProperties {

    /** 是否启用深度分析；关闭则回退到原有 Tika 纯文本抽取 */
    private boolean enabled = true;

    /**
     * 是否调用视觉模型处理图片与扫描页。
     * 关闭后图片与扫描件内容将不被识别（元素仍会记录，但 content 为空）。
     */
    private boolean visionEnabled = true;

    /** 单个文档最多处理的图片数量，超出部分跳过（避免大文档产生巨额调用成本） */
    private int maxImagesPerDocument = 20;

    /** 单个文档最多处理的扫描页数，超出部分跳过 */
    private int maxScannedPagesPerDocument = 30;

    /**
     * 分析结果是否落库。
     * 关闭后元素元数据仅存在于本次调用结果中，不写入 ai_document_element 表。
     */
    private boolean persistElements = true;

    /**
     * 深度分析的超时时间（秒）。
     * 超过该时长将中断分析并回退到纯文本抽取，保证上传流程不被拖垮。
     */
    private int timeoutSeconds = 300;
}
