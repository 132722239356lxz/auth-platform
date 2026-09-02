package com.liang.xz.aiagent.agent.multi;

import com.liang.xz.aiagent.agent.AgentTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * <p>知识检索 Agent —— 负责从知识库中查找资料（RAG 检索侧）</p>
 *
 * <p>委托 {@link AgentTools} 的检索能力：向量语义检索、Lucene 本地全文检索，
 * 以及知识库清单列举。检索结果可作为下游 Agent（如汇总 Agent）的事实依据。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeAgent implements Agent {

    private final AgentTools agentTools;

    @Override
    public String agentId() {
        return "knowledge";
    }

    @Override
    public String description() {
        return "从知识库中检索文档内容，支持语义检索与关键词全文检索；"
                + "适合「某某流程怎么走」「制度是怎么规定的」「查一下文档」这类需要依据资料回答的问题";
    }

    @Override
    public boolean canHandle(String task, AgentContext ctx) {
        if (task == null) {
            return false;
        }
        return task.contains("知识") || task.contains("文档") || task.contains("资料")
                || task.contains("规定") || task.contains("流程") || task.contains("制度")
                || task.contains("手册") || task.contains("怎么") || task.contains("如何");
    }

    @Override
    public String execute(String task, AgentContext ctx) {
        // 任务文本中可能包含编排器注入的"总体问题/前置任务产出"等结构，
        // 这里截取【本次任务】段落作为检索词，避免把无关上下文当查询词导致检索漂移
        String query = extractTaskContent(task);
        try {
            String semantic = agentTools.searchKnowledge(query);
            if (semantic != null && !semantic.isBlank() && !isNoResult(semantic)) {
                return semantic;
            }
            // 语义检索无果时回退关键词检索，提高召回率
            String local = agentTools.localSearch(query);
            if (local != null && !local.isBlank()) {
                return local;
            }
            return semantic == null || semantic.isBlank()
                    ? "未在知识库中检索到相关内容。"
                    : semantic;
        } catch (Exception e) {
            log.warn("[KnowledgeAgent] 检索失败: {}", e.getMessage());
            return "知识检索失败：" + e.getMessage();
        }
    }

    /**
     * 从带结构标记的提示词中提取实际任务内容。
     */
    private String extractTaskContent(String task) {
        if (task == null) {
            return "";
        }
        int start = task.indexOf("【本次任务】");
        if (start < 0) {
            return task.trim();
        }
        String rest = task.substring(start + "【本次任务】".length());
        int end = rest.indexOf("【前置任务");
        if (end > 0) {
            rest = rest.substring(0, end);
        }
        return rest.trim();
    }

    /**
     * 判断检索结果是否为"无命中"，不同实现返回的空结果措辞不同，故做包含判断。
     */
    private boolean isNoResult(String result) {
        String lower = result.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("未找到") || lower.contains("无结果")
                || lower.contains("没有找到") || lower.contains("no result")
                || lower.contains("空");
    }
}
