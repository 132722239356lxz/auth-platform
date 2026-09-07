package com.liang.xz.flow.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.flow.entity.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 条件表达式求值器
 *
 * <p>使用 Spring Expression Language (SpEL) 评估条件:</p>
 *
 * <pre>
 * 内置变量:
 *   #applyContent     — 申请内容Map (从applyContent JSON解析)
 *   #applicant        — 申请人
 *   #currentUser      — 当前操作用户
 *   #approvalHistory  — 审批历史 Map{nodeName: action}
 *
 * 示例表达式:
 *   "#applyContent['level'] >= 3"
 *   "#applyContent['amount'] > 10000"
 *   "#applyContent['department'] == 'IT'"
 *   "#approvalHistory['部门经理审批'] == 'APPROVED'"
 *   "(#applyContent['level'] >= 3) and (#applyContent['amount'] > 5000)"
 * </pre>
 */
@Slf4j
@Component
public class ConditionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 评估条件节点
     *
     * @param node             条件节点
     * @param applyContentJson 申请内容JSON
     * @param applicant        申请人
     * @param currentUser      当前用户
     * @param approvalHistory  审批历史
     * @return true=条件满足, false=不满足
     */
    public boolean evaluate(WorkflowNode node, String applyContentJson, String applicant,
                            String currentUser, Map<String, String> approvalHistory) {
        if (node.getConditionExpression() == null || node.getConditionExpression().isBlank()) {
            log.warn("[Condition] 节点 {} 未配置条件表达式, 默认返回true", node.getNodeName());
            return true;
        }

        try {
            StandardEvaluationContext context = new StandardEvaluationContext();

            // 解析申请内容JSON为Map
            Map<String, Object> applyContent = parseApplyContent(applyContentJson);
            context.setVariable("applyContent", applyContent);
            context.setVariable("applicant", applicant);
            context.setVariable("currentUser", currentUser);
            context.setVariable("approvalHistory", approvalHistory != null ? approvalHistory : Map.of());

            Boolean result = parser.parseExpression(node.getConditionExpression())
                    .getValue(context, Boolean.class);

            boolean passed = Boolean.TRUE.equals(result);
            log.info("[Condition] 节点 '{}' 评估结果: expr='{}', passed={}, applyContent={}",
                    node.getNodeName(), node.getConditionExpression(), passed, applyContent);
            return passed;
        } catch (Exception e) {
            log.error("[Condition] 条件表达式求值异常: node={}, expr={}",
                    node.getNodeName(), node.getConditionExpression(), e);
            return false; // 解析异常视为不满足, 安全策略
        }
    }

    /**
     * 解析申请内容 JSON 为 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseApplyContent(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[Condition] 申请内容JSON解析失败, 返回空Map: {}", json);
            return new HashMap<>();
        }
    }
}
