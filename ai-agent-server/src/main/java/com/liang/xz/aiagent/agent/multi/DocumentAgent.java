package com.liang.xz.aiagent.agent.multi;

import com.liang.xz.aiagent.repository.DocumentElementRepository;
import com.liang.xz.aiagent.service.document.DocumentAnalysisService;
import com.liang.xz.aiagent.service.document.model.DocumentAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * <p>文档解析 Agent —— 在对话中按需解析并解读已上传的文档</p>
 *
 * <p><b>定位：</b>文件上传时的深度分析由 {@link DocumentAnalysisService} 在入库流程中同步完成；
 * 本 Agent 负责<b>对话中</b>的文档相关请求，例如"这份文档有几个表格""第 3 页的图片讲了什么"
 * "总结一下这份扫描件"，通过查询已落库的元素元数据作答。</p>
 *
 * <p><b>为什么查库而不是重新解析：</b>重新解析会重复触发视觉模型调用，成本高且慢。
 * 元素元数据已包含类型、页码、内容，足以支撑绝大多数文档问答。</p>
 *
 * <p><b>注册说明：</b>实现 {@link Agent} 后会被 {@link AgentRegistry} 自动收集，
 * 无需手工登记，编排器即可按 {@code agentId="document"} 分派任务。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentAgent implements Agent {

    private final DocumentElementRepository documentElementRepository;
    private final DocumentAnalysisService documentAnalysisService;

    @Override
    public String agentId() {
        return "document";
    }

    @Override
    public String description() {
        return "查询和解读已上传文档的结构化内容，包括表格数据、图片与图表描述、扫描件识别文本、"
                + "页眉页脚信息，以及各元素所在页码与位置；"
                + "适合「文档里有哪些表格」「第几页的图片内容」「这份扫描件说了什么」这类问题";
    }

    @Override
    public boolean canHandle(String task, AgentContext ctx) {
        if (task == null) {
            return false;
        }
        return task.contains("文档") || task.contains("表格") || task.contains("图片")
                || task.contains("扫描") || task.contains("附件") || task.contains("页眉")
                || task.contains("页脚") || task.contains("PDF") || task.contains("Word");
    }

    /**
     * 执行文档相关任务。
     *
     * <p>当前实现基于已落库的元素元数据作答。任务中若包含 {@code docId=xxx} 形式，
     * 则聚焦该文档；否则返回文档处理能力的说明，由上层（或 LLM）决定后续动作。</p>
     */
    @Override
    public String execute(String task, AgentContext ctx) {
        Long docId = extractDocId(task);
        if (docId == null) {
            return "文档解析 Agent 需要先指定文档ID（格式 docId=123）。"
                    + "当前支持查询的元素类型：表格、图片、图表、扫描页、页眉、页脚。";
        }
        try {
            List<Map<String, Object>> elements = documentElementRepository.findByDocId(docId);
            if (elements.isEmpty()) {
                return "文档 " + docId + " 暂无元素元数据，可能尚未完成深度解析，或该文档不是 PDF/Word 格式。";
            }
            return formatElements(docId, elements, task);
        } catch (Exception e) {
            log.warn("[DocumentAgent] 查询文档元素失败: docId={}, reason={}", docId, e.getMessage());
            return "查询文档元素失败：" + e.getMessage();
        }
    }

    /**
     * 从任务文本中提取 docId（支持 "docId=123" 与 "文档ID:123" 两种写法）。
     */
    private Long extractDocId(String task) {
        if (task == null) {
            return null;
        }
        String[] patterns = {"docId=", "docid=", "文档ID:", "文档id:"};
        String lower = task.toLowerCase();
        for (String pattern : patterns) {
            int index = lower.indexOf(pattern.toLowerCase());
            if (index >= 0) {
                String rest = task.substring(index + pattern.length()).trim();
                StringBuilder digits = new StringBuilder();
                for (int i = 0; i < rest.length() && i < 20; i++) {
                    char c = rest.charAt(i);
                    if (Character.isDigit(c)) {
                        digits.append(c);
                    } else if (digits.length() > 0) {
                        break;
                    }
                }
                if (digits.length() > 0) {
                    try {
                        return Long.parseLong(digits.toString());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 把元素元数据格式化为可读文本。
     *
     * <p>若任务限定了元素类型（如只要表格），则只输出该类型，避免无关内容干扰后续汇总。</p>
     */
    private String formatElements(Long docId, List<Map<String, Object>> elements, String task) {
        String wantedType = detectWantedType(task);
        StringBuilder sb = new StringBuilder();
        sb.append("文档 ").append(docId).append(" 共 ").append(elements.size()).append(" 个元素");
        if (wantedType != null) {
            sb.append("（已按「").append(wantedType).append("」筛选）");
        }
        sb.append("：\n");

        int shown = 0;
        for (Map<String, Object> row : elements) {
            String type = str(row.get("element_type"));
            if (wantedType != null && !matchesWanted(type, wantedType)) {
                continue;
            }
            Object page = row.get("page_no");
            String content = str(row.get("content"));
            sb.append("- [").append(type).append("]");
            if (page != null && ((Number) page).intValue() > 0) {
                sb.append(" 第").append(page).append("页");
            }
            sb.append(" 置信度").append(fmt(row.get("confidence")))
                    .append(" 方式").append(str(row.get("processing_method")));
            String remark = str(row.get("remark"));
            if (remark != null && !remark.isBlank()) {
                sb.append(" (").append(remark).append(")");
            }
            sb.append("\n  ").append(content == null ? "" : abbreviate(content, 500)).append("\n");
            if (++shown >= 30) {
                sb.append("...（仅显示前 30 个元素）\n");
                break;
            }
        }
        if (shown == 0) {
            sb.append("（未找到符合条件的元素）\n");
        }
        return sb.toString();
    }

    /**
     * 检测任务是否限定了元素类型。
     */
    private String detectWantedType(String task) {
        if (task == null) {
            return null;
        }
        if (task.contains("表格")) {
            return "TABLE";
        }
        if (task.contains("图片") || task.contains("图表")) {
            return "IMAGE";
        }
        if (task.contains("扫描")) {
            return "SCANNED_PAGE";
        }
        if (task.contains("页眉")) {
            return "HEADER";
        }
        if (task.contains("页脚")) {
            return "FOOTER";
        }
        return null;
    }

    private boolean matchesWanted(String type, String wanted) {
        if (type == null) {
            return false;
        }
        if ("IMAGE".equals(wanted)) {
            return "IMAGE".equals(type) || "CHART".equals(type);
        }
        return wanted.equals(type);
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String fmt(Object value) {
        if (value == null) {
            return "未知";
        }
        try {
            return String.format("%.2f", ((Number) value).doubleValue());
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String abbreviate(String text, int max) {
        String flat = text.replace("\n", " ").trim();
        return flat.length() <= max ? flat : flat.substring(0, max) + "...";
    }
}
