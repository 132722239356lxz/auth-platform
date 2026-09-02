package com.liang.xz.aiagent.service.document.model;

/**
 * <p>视觉处理接口 —— 把图片字节转为文字描述</p>
 *
 * <p><b>为什么抽成接口：</b>提取器（PDF/Word）不应直接依赖 {@code LlmClient}，
 * 否则解析逻辑与模型调用耦死，无法单独测试，也无法在视觉模型不可用时替换为其它实现
 * （如本地 OCR 引擎）。通过此接口，提取器只关心"给我图片，还我文字"。</p>
 *
 * <p><b>实现方式：</b>项目内默认由 {@code LlmClient.describeImage} 承载（视觉大模型），
 * 相比传统 OCR 引擎（如 Tesseract）的优势是无需额外部署语言包，且能理解图表语义；
 * 代价是每次调用有网络与 token 成本，因此由配置控制开关与调用上限。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public interface VisionProcessor {

    /**
     * 识别图片内容。
     *
     * @param imageBytes 图片字节（建议使用 PNG）
     * @param mimeType   图片 MIME 类型
     * @return 图片的文字描述；识别失败返回 null 或空串
     */
    String describe(byte[] imageBytes, String mimeType);

    /**
     * 当前视觉能力是否可用（未配置视觉模型时返回 false，提取器会跳过图片处理）。
     */
    boolean isAvailable();
}
