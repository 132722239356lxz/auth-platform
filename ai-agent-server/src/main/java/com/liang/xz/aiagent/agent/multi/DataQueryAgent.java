package com.liang.xz.aiagent.agent.multi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.aiagent.agent.DataQueryTool;
import com.liang.xz.aiagent.llm.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>数据查询 Agent —— 把自然语言问题转换为结构化查询并落库执行</p>
 *
 * <p><b>为什么需要本 Agent：</b>{@link DataQueryTool} 出于防注入考虑，只接受结构化参数
 * （表名、字段、过滤条件），不接收 SQL 文本。但编排器分派来的是自然语言任务，
 * 因此需要一个"翻译层"：由 LLM 生成结构化查询参数，再交给工具执行。</p>
 *
 * <p><b>安全边界：</b>LLM 输出<b>不可信</b>，绝不能被当作 SQL 直接执行。
 * 本 Agent 只解析出参数对象，真正的表/字段/函数白名单校验仍在
 * {@link DataQueryTool} 内完成，任何越界参数都会被工具层拒绝。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataQueryAgent implements Agent {

    private static final Pattern JSON_BLOCK =
            Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final DataQueryTool dataQueryTool;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    @Override
    public String agentId() {
        return "query";
    }

    @Override
    public String description() {
        return "按条件查询业务表数据或做聚合统计（COUNT/SUM/AVG/MAX/MIN），支持分组、过滤、排序；"
                + "适合「某个表有多少条记录」「按状态统计数量」「查询满足某条件的明细」这类问题";
    }

    @Override
    public boolean canHandle(String task, AgentContext ctx) {
        if (task == null) {
            return false;
        }
        return task.contains("查询") || task.contains("统计") || task.contains("聚合")
                || task.contains("多少") || task.contains("计数") || task.contains("汇总")
                || task.contains("分组") || task.contains("表");
    }

    @Override
    public String execute(String task, AgentContext ctx) {
        String queryPlan = planQuery(task);
        if (queryPlan == null || queryPlan.isBlank()) {
            return "无法将该任务转换为结构化查询：" + task;
        }
        try {
            JsonNode plan = objectMapper.readTree(queryPlan);
            if (plan == null || !plan.isObject()) {
                return "查询计划格式不合法";
            }
            String mode = text(plan, "mode");
            String table = text(plan, "table");
            if (table == null || table.isBlank()) {
                return "查询计划缺少表名";
            }

            List<String> groupBy = toStringList(plan.get("groupBy"));
            List<DataQueryTool.QueryFilter> filters = parseFilters(plan.get("filters"));

            DataQueryTool.DataQueryResult result;
            if ("aggregate".equalsIgnoreCase(mode)) {
                String func = defaultStr(text(plan, "aggregateFunc"), "COUNT");
                String column = defaultStr(text(plan, "column"), "*");
                result = dataQueryTool.aggregate(table, func, column, groupBy, filters);
            } else {
                List<String> columns = toStringList(plan.get("columns"));
                String orderBy = text(plan, "orderBy");
                int limit = plan.get("limit") != null && plan.get("limit").isNumber()
                        ? plan.get("limit").asInt() : 100;
                result = dataQueryTool.query(table, columns, filters, groupBy, orderBy, limit);
            }
            return formatResult(result);
        } catch (Exception e) {
            log.warn("[DataQueryAgent] 结构化查询执行失败: {}", e.getMessage());
            return "数据查询失败：" + e.getMessage();
        }
    }

    /**
     * 用 LLM 把自然语言任务转成结构化查询参数 JSON。
     */
    private String planQuery(String task) {
        String systemPrompt = """
                你是一个结构化查询生成器。请将用户的数据查询需求转换为 JSON 查询计划。

                可用的表与字段信息：
                %s

                输出要求（只输出 JSON，不要解释，不要 markdown 代码块）：
                {
                  "mode": "query" 或 "aggregate",
                  "table": "表名（必须是上表中存在的）",
                  "columns": ["字段1","字段2"],        // 仅 mode=query 时需要
                  "aggregateFunc": "COUNT|SUM|AVG|MAX|MIN",  // 仅 mode=aggregate 时需要
                  "column": "字段名或*",                  // 仅 mode=aggregate 时需要
                  "groupBy": ["字段名"],
                  "orderBy": "字段名 DESC",
                  "limit": 数字,
                  "filters": [
                     {"field":"字段名","op":"EQ|NE|GT|GE|LT|LE|LIKE|IN|IS_NULL|IS_NOT_NULL","value":值},
                     {"field":"字段名","op":"GE","valueExpr":"CURDATE()"}
                  ]
                }

                规则：
                1. 表名必须是白名单中的表，不得臆造
                2. 字段名只能包含字母、数字、下划线
                3. 时间范围比较用 valueExpr，只允许 CURDATE()、NOW()、DATE_SUB(NOW(), INTERVAL n DAY/HOUR/MINUTE/MONTH)
                4. 普通值用 value，不要用 valueExpr
                """.formatted(dataQueryTool.getMetadata());

        try {
            String response = llmClient.chat(systemPrompt, "查询需求：" + task);
            if (response == null) {
                return null;
            }
            String json = response.trim();
            Matcher matcher = JSON_BLOCK.matcher(json);
            if (matcher.find()) {
                json = matcher.group(1).trim();
            } else {
                int start = json.indexOf('{');
                int end = json.lastIndexOf('}');
                if (start >= 0 && end > start) {
                    json = json.substring(start, end + 1);
                }
            }
            return json;
        } catch (Exception e) {
            log.warn("[DataQueryAgent] 生成查询计划失败: {}", e.getMessage());
            return null;
        }
    }

    private List<DataQueryTool.QueryFilter> parseFilters(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<DataQueryTool.QueryFilter> filters = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String field = text(item, "field");
            if (field == null || field.isBlank()) {
                continue;
            }
            DataQueryTool.QueryFilter.QueryFilterBuilder builder =
                    DataQueryTool.QueryFilter.builder()
                            .field(field)
                            .op(defaultStr(text(item, "op"), "EQ"));

            JsonNode exprNode = item.get("valueExpr");
            if (exprNode != null && exprNode.isTextual() && !exprNode.asText().isBlank()) {
                builder.valueExpr(exprNode.asText().trim());
            } else {
                JsonNode valueNode = item.get("value");
                if (valueNode != null && !valueNode.isNull()) {
                    builder.value(convertValue(valueNode));
                }
            }
            filters.add(builder.build());
        }
        return filters;
    }

    private Object convertValue(JsonNode node) {
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(item -> list.add(convertValue(item)));
            return list;
        }
        return node.asText();
    }

    private List<String> toStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && item.isTextual()) {
                list.add(item.asText());
            }
        }
        return list;
    }

    private String formatResult(DataQueryTool.DataQueryResult result) {
        if (result == null) {
            return "查询未返回结果";
        }
        if (!result.isSuccess()) {
            return "查询失败：" + result.getError();
        }
        if (result.getData() == null || result.getData().isEmpty()) {
            return "查询结果为空";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("查询成功，共 ").append(result.getRowCount()).append(" 行");
        if (result.getElapsedMs() > 0) {
            sb.append("，耗时 ").append(result.getElapsedMs()).append("ms");
        }
        sb.append("：\n");
        int limit = Math.min(result.getData().size(), 30);
        for (int i = 0; i < limit; i++) {
            sb.append(result.getData().get(i)).append("\n");
        }
        if (result.getData().size() > limit) {
            sb.append("...（仅显示前 ").append(limit).append(" 行）");
        }
        return sb.toString();
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode child = node.get(field);
        return child == null || child.isNull() ? null : child.asText();
    }

    /**
     * 为空时返回默认值。
     *
     * <p><b>注意：此处刻意不做大小写转换。</b>表名与字段名在 MySQL 中是否区分大小写
     * 取决于系统变量 lower_case_table_names 与操作系统（Linux 下默认区分），
     * 若在此统一转大写，模型给出的 {@code create_time} 会变成 {@code CREATE_TIME} 而查不到列。
     * 聚合函数与操作符的大小写归一由 {@link DataQueryTool} 内部负责，本类无需处理。</p>
     */
    private String defaultStr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
