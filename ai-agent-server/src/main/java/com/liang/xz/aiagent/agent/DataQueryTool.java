package com.liang.xz.aiagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * <p>数据查询工具 — 智能体可调用的数据库查询工具</p>
 *
 * <p><b>安全设计（防 SQL 注入）：</b>本工具不接收任何自由格式的 SQL 文本。
 * LLM 只能传入结构化参数（表名、字段名、过滤条件值），SQL 由本类在服务端拼装：
 * <ul>
 *   <li><b>标识符（表名/字段名）：</b>必须匹配 {@link #IDENTIFIER} 正则且命中白名单，
 *       杜绝 `` `id`; DROP TABLE x --`` 之类的注入；</li>
 *   <li><b>值：</b>一律通过 {@code :paramN} 占位符由 JDBC 参数化绑定，不参与 SQL 文本拼接；</li>
 *   <li><b>聚合函数/操作符：</b>枚举白名单，非法即拒绝；</li>
 *   <li><b>兜底：</b>强制 LIMIT 上限、强制只读连接。</li>
 * </ul>
 * </p>
 *
 * <p>历史版本曾直接执行 LLM 生成的 SQL 字符串，存在严重注入风险（LLM 输出可被提示词注入操控），
 * 现已重构为结构化查询，不再暴露 SQL 文本入口。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
public class DataQueryTool {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    /** 允许查询的表白名单 */
    private static final Set<String> ALLOWED_TABLES = Set.of(
            "wf_instance", "wf_task", "wf_definition", "wf_node",
            "sys_user", "sys_role", "msg_record",
            "ai_knowledge_doc", "ai_analysis_alert", "ai_search_record"
    );

    /** 允许的聚合函数白名单 */
    private static final Set<String> ALLOWED_AGGREGATE_FUNCS = Set.of("COUNT", "SUM", "AVG", "MAX", "MIN");

    /** 允许的过滤操作符白名单（键为对外名称，值为 SQL 片段） */
    private static final Map<String, String> ALLOWED_OPERATORS = Map.of(
            "EQ", "=",
            "NE", "<>",
            "GT", ">",
            "GE", ">=",
            "LT", "<",
            "LE", "<=",
            "LIKE", "LIKE",
            "IN", "IN",
            "IS_NULL", "IS NULL",
            "IS_NOT_NULL", "IS NOT NULL"
    );

    /** 合法 SQL 标识符：字母或下划线开头，仅含字母、数字、下划线 */
    private static final Pattern IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /**
     * 允许的 SQL 时间函数白名单（用于时间范围类指标，如"今日""近7天"）。
     * <p>值一律参数化绑定，但形如 {@code create_time >= CURDATE()} 的 comparisons 需要 SQL 函数，
     * 无法参数化。此处采用<b>严格正则白名单</b>：只有完全匹配这些固定形态的函数才被拼入 SQL，
     * 任何自由文本（含注入 payload）都会被拒绝。切勿放宽该正则。</p>
     */
    private static final Pattern SAFE_SQL_FUNCTION = Pattern.compile(
            "^(CURDATE\\(\\)|NOW\\(\\)|"
            + "DATE_(SUB|ADD)\\(\\s*NOW\\(\\)\\s*,\\s*INTERVAL\\s+\\d{1,4}\\s+"
            + "(DAY|HOUR|MINUTE|MONTH)\\s*\\))$",
            Pattern.CASE_INSENSITIVE);

    /** 单次查询返回行数上限，防止大结果集打爆内存 */
    private static final int MAX_ROWS = 500;

    public DataQueryTool(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * 结构化数据查询 —— 仅接收表名/字段/过滤条件，SQL 由服务端拼装。
     *
     * @param table    表名，必须在 {@link #ALLOWED_TABLES} 内
     * @param columns  返回字段列表，每项须为合法标识符（为空则返回全部字段）
     * @param filters  过滤条件列表，字段须合法、值走参数化绑定
     * @param groupBy  分组字段列表（可为空）
     * @param orderBy  排序字段（可为空），格式 "字段名" 或 "字段名 DESC"
     * @param limit    返回行数（会被 {@link #MAX_ROWS} 限制）
     */
    @Tool("结构化查询业务表数据。只能查白名单内的表，字段名/条件值由服务端安全拼装，禁止传入完整SQL")
    public DataQueryResult query(String table, List<String> columns, List<QueryFilter> filters,
                                 List<String> groupBy, String orderBy, Integer limit) {
        if (!ALLOWED_TABLES.contains(table)) {
            return DataQueryResult.error("不允许查询该表: " + table + "，可用表: " + ALLOWED_TABLES);
        }
        try {
            List<String> safeColumns = validateIdentifiers(columns, "columns");
            List<String> safeGroupBy = validateIdentifiers(groupBy, "groupBy");
            String safeOrderBy = buildOrderBy(orderBy);

            StringBuilder sql = new StringBuilder("SELECT ");
            sql.append(safeColumns.isEmpty() ? "*" : String.join(", ", safeColumns));
            sql.append(" FROM ").append(table);

            MapSqlParameterSource paramSource = new MapSqlParameterSource();
            String where = buildWhere(filters, paramSource);
            if (!where.isEmpty()) {
                sql.append(" WHERE ").append(where);
            }
            if (!safeGroupBy.isEmpty()) {
                sql.append(" GROUP BY ").append(String.join(", ", safeGroupBy));
            }
            if (safeOrderBy != null) {
                sql.append(" ORDER BY ").append(safeOrderBy);
            }
            sql.append(" LIMIT ").append(resolveLimit(limit));

            long start = System.currentTimeMillis();
            List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), paramSource);
            long elapsed = System.currentTimeMillis() - start;
            log.debug("[DataQueryTool] 结构化查询: table={} rows={} cost={}ms", table, rows.size(), elapsed);
            return DataQueryResult.success(rows, elapsed);
        } catch (IllegalArgumentException e) {
            log.warn("[DataQueryTool] 非法查询参数被拒绝: table={}, reason={}", table, e.getMessage());
            return DataQueryResult.error("查询参数非法: " + e.getMessage());
        } catch (Exception e) {
            log.error("[DataQueryTool] 查询失败: table={}", table, e);
            return DataQueryResult.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 聚合统计查询 —— 字段与分组均做标识符校验，条件值参数化。
     */
    @Tool("按维度聚合统计业务表数据(COUNT/SUM/AVG/MAX/MIN)，支持分组与过滤，禁止传入完整SQL")
    public DataQueryResult aggregate(String table, String aggregateFunc, String column,
                                     List<String> groupBy, List<QueryFilter> filters) {
        if (!ALLOWED_TABLES.contains(table)) {
            return DataQueryResult.error("不允许查询该表: " + table + "，可用表: " + ALLOWED_TABLES);
        }
        String func = aggregateFunc == null ? "COUNT" : aggregateFunc.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_AGGREGATE_FUNCS.contains(func)) {
            return DataQueryResult.error("不支持的聚合函数: " + aggregateFunc
                    + "，可用: " + ALLOWED_AGGREGATE_FUNCS);
        }
        try {
            // COUNT(*) 场景允许 column 为 "*"，其余必须是合法标识符
            String safeColumn;
            if ("*".equals(column)) {
                if (!"COUNT".equals(func)) {
                    return DataQueryResult.error("仅 COUNT 支持 * ，其它聚合函数需指定字段");
                }
                safeColumn = "*";
            } else {
                safeColumn = validateIdentifier(column, "column");
            }
            List<String> safeGroupBy = validateIdentifiers(groupBy, "groupBy");

            StringBuilder sql = new StringBuilder("SELECT ");
            if (!safeGroupBy.isEmpty()) {
                sql.append(String.join(", ", safeGroupBy)).append(", ");
            }
            sql.append(func).append("(").append(safeColumn).append(") AS agg_value FROM ").append(table);

            MapSqlParameterSource paramSource = new MapSqlParameterSource();
            String where = buildWhere(filters, paramSource);
            if (!where.isEmpty()) {
                sql.append(" WHERE ").append(where);
            }
            if (!safeGroupBy.isEmpty()) {
                sql.append(" GROUP BY ").append(String.join(", ", safeGroupBy));
            }

            long start = System.currentTimeMillis();
            List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), paramSource);
            long elapsed = System.currentTimeMillis() - start;
            log.debug("[DataQueryTool] 聚合查询: table={} func={} rows={} cost={}ms",
                    table, func, rows.size(), elapsed);
            return DataQueryResult.success(rows, elapsed);
        } catch (IllegalArgumentException e) {
            log.warn("[DataQueryTool] 非法聚合参数被拒绝: table={}, reason={}", table, e.getMessage());
            return DataQueryResult.error("聚合参数非法: " + e.getMessage());
        } catch (Exception e) {
            log.error("[DataQueryTool] 聚合查询失败: table={}", table, e);
            return DataQueryResult.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取可用表和字段元数据(供 LLM 生成查询参数)
     */
    @Tool("获取允许查询的数据表清单与查询示例，在生成查询前先调用以了解可用表")
    public Map<String, Object> getMetadata() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("availableTables", ALLOWED_TABLES);
        meta.put("availableAggregateFuncs", ALLOWED_AGGREGATE_FUNCS);
        meta.put("availableOperators", ALLOWED_OPERATORS.keySet());
        meta.put("maxRows", MAX_ROWS);
        meta.put("exampleQueries", List.of(
                "query(table='wf_instance', columns=['id','status'], filters=[{field:'status',op:'EQ',value:'RUNNING'}], limit=50)",
                "aggregate(table='wf_instance', aggregateFunc='COUNT', column='*', groupBy=['status'])",
                "query(table='ai_analysis_alert', filters=[{field:'resolved',op:'EQ',value:0},{field:'alert_level',op:'EQ',value:'CRITICAL'}])"));
        return meta;
    }

    // ======================== 安全校验与 SQL 拼装 ========================

    /**
     * 校验单个标识符合法性（防注入关键：拒绝分号、空格、引号、注释符等）。
     */
    private String validateIdentifier(String identifier, String paramName) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException(paramName + " 不能为空");
        }
        String trimmed = identifier.trim();
        if (!IDENTIFIER.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(paramName + " 含非法字符，仅允许字母/数字/下划线: " + trimmed);
        }
        return trimmed;
    }

    /**
     * 批量校验标识符列表。
     */
    private List<String> validateIdentifiers(List<String> identifiers, String paramName) {
        if (identifiers == null || identifiers.isEmpty()) {
            return List.of();
        }
        List<String> safe = new ArrayList<>(identifiers.size());
        for (String item : identifiers) {
            if (item == null || item.isBlank()) {
                continue;
            }
            safe.add(validateIdentifier(item, paramName));
        }
        return safe;
    }

    /**
     * 构造 WHERE 子句：字段名做标识符校验，值一律走命名参数绑定。
     */
    private String buildWhere(List<QueryFilter> filters, MapSqlParameterSource paramSource) {
        if (filters == null || filters.isEmpty()) {
            return "";
        }
        List<String> conditions = new ArrayList<>(filters.size());
        int index = 0;
        for (QueryFilter filter : filters) {
            if (filter == null || filter.field == null || filter.field.isBlank()) {
                throw new IllegalArgumentException("过滤条件缺少 field");
            }
            String field = validateIdentifier(filter.field, "filters.field");
            String opKey = filter.op == null ? "EQ" : filter.op.trim().toUpperCase(Locale.ROOT);
            String sqlOp = ALLOWED_OPERATORS.get(opKey);
            if (sqlOp == null) {
                throw new IllegalArgumentException("不支持的操作符: " + filter.op
                        + "，可用: " + ALLOWED_OPERATORS.keySet());
            }

            if ("IS_NULL".equals(opKey) || "IS_NOT_NULL".equals(opKey)) {
                conditions.add(field + " " + sqlOp);
                continue;
            }
            if (filter.value == null) {
                throw new IllegalArgumentException("过滤条件缺少 value: " + field);
            }
            if ("IN".equals(opKey)) {
                Collection<?> values = toCollection(filter.value);
                if (values.isEmpty()) {
                    throw new IllegalArgumentException("IN 条件的值不能为空: " + field);
                }
                List<String> placeholders = new ArrayList<>(values.size());
                int i = 0;
                for (Object v : values) {
                    String name = "p" + index + "_" + i;
                    placeholders.add(":" + name);
                    paramSource.addValue(name, v);
                    i++;
                }
                conditions.add(field + " IN (" + String.join(", ", placeholders) + ")");
            } else if (filter.valueExpr != null && !filter.valueExpr.isBlank()) {
                // SQL 函数比较：必须完全匹配白名单正则，否则拒绝
                String expr = filter.valueExpr.trim();
                if (!SAFE_SQL_FUNCTION.matcher(expr).matches()) {
                    throw new IllegalArgumentException(
                            "valueExpr 只允许白名单内的时间函数(CURDATE()/NOW()/DATE_SUB(NOW(), INTERVAL n DAY))，"
                            + "实际值: " + expr);
                }
                conditions.add(field + " " + sqlOp + " " + expr);
            } else {
                String name = "p" + index;
                conditions.add(field + " " + sqlOp + " :" + name);
                paramSource.addValue(name, filter.value);
            }
            index++;
        }
        return String.join(" AND ", conditions);
    }

    /**
     * 构造 ORDER BY：仅允许 "字段" 或 "字段 ASC|DESC"，方向枚举校验。
     */
    private String buildOrderBy(String orderBy) {
        if (orderBy == null || orderBy.isBlank()) {
            return null;
        }
        String[] parts = orderBy.trim().split("\\s+");
        if (parts.length > 2) {
            throw new IllegalArgumentException("orderBy 格式非法，仅支持 '字段' 或 '字段 DESC'");
        }
        String field = validateIdentifier(parts[0], "orderBy");
        if (parts.length == 1) {
            return field;
        }
        String direction = parts[1].toUpperCase(Locale.ROOT);
        if (!"ASC".equals(direction) && !"DESC".equals(direction)) {
            throw new IllegalArgumentException("orderBy 排序方向仅支持 ASC/DESC: " + direction);
        }
        return field + " " + direction;
    }

    /**
     * 限制返回行数，避免全表扫描打爆内存。
     */
    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return MAX_ROWS;
        }
        return Math.min(limit, MAX_ROWS);
    }

    /**
     * 将 IN 条件的值转为集合（兼容单个值、逗号分隔字符串、集合类型）。
     */
    private Collection<?> toCollection(Object value) {
        if (value instanceof Collection<?> coll) {
            return coll;
        }
        if (value instanceof Object[] arr) {
            return Arrays.asList(arr);
        }
        if (value instanceof String str) {
            return Arrays.asList(str.split(","));
        }
        return List.of(value);
    }

    // ======================== 出入参模型 ========================

    /**
     * 结构化过滤条件。
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class QueryFilter {
        /** 字段名，须为合法标识符 */
        private String field;
        /** 操作符，取值见 ALLOWED_OPERATORS（EQ/NE/GT/GE/LT/LE/LIKE/IN/IS_NULL/IS_NOT_NULL） */
        private String op;
        /** 比较值，走参数化绑定，不拼进 SQL 文本 */
        private Object value;
        /**
         * SQL 函数表达式（可选，用于时间范围比较，如 {@code CURDATE()}、
         * {@code DATE_SUB(NOW(), INTERVAL 7 DAY)}）。
         * 必须匹配 {@link #SAFE_SQL_FUNCTION} 白名单，否则查询被拒绝。
         * 设置该项时忽略 {@link #value}。
         */
        private String valueExpr;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DataQueryResult {
        private boolean success;
        private String error;
        private List<Map<String, Object>> data;
        private int rowCount;
        private long elapsedMs;

        public static DataQueryResult success(List<Map<String, Object>> data, long elapsed) {
            return DataQueryResult.builder()
                    .success(true).data(data).rowCount(data.size()).elapsedMs(elapsed).build();
        }

        public static DataQueryResult error(String msg) {
            return DataQueryResult.builder().success(false).error(msg).build();
        }
    }
}
