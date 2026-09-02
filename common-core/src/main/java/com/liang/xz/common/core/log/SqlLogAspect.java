package com.liang.xz.common.core.log;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 执行日志切面
 * <p>
 * 拦截 JdbcTemplate 的所有数据操作，记录调用方法、SQL语句、绑定参数、执行结果和耗时。
 * <p>
 * 启用条件：配置 sql.log.enabled=true
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Aspect
@Component
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnProperty(value = "sql.log.enabled", havingValue = "true")
public class SqlLogAspect {

    private static final Logger log = LoggerFactory.getLogger(SqlLogAspect.class);

    private static final int MAX_SQL_LENGTH = 2000;

    /** 切入 JdbcTemplate 所有查询方法 */
    @Pointcut("execution(* org.springframework.jdbc.core.JdbcTemplate.query*(..))")
    public void queryExecution() {
    }

    /** 切入 JdbcTemplate 所有更新方法 */
    @Pointcut("execution(* org.springframework.jdbc.core.JdbcTemplate.update*(..))")
    public void updateExecution() {
    }

    /** 切入 JdbcTemplate 批量更新方法 */
    @Pointcut("execution(* org.springframework.jdbc.core.JdbcTemplate.batchUpdate*(..))")
    public void batchExecution() {
    }

    /** 切入 JdbcTemplate execute 方法 */
    @Pointcut("execution(* org.springframework.jdbc.core.JdbcTemplate.execute(..))")
    public void executeExecution() {
    }

    @Around("queryExecution() || updateExecution() || batchExecution() || executeExecution()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        String methodName = joinPoint.getSignature().getName();
        String sql = extractSql(args);
        String bindParams = extractBindParams(args);
        String caller = resolveCaller();

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long cost = System.currentTimeMillis() - start;
            log.info("[SQL] jdbc.{} | caller={} | sql=[{}] | params=[{}] | result={} | {}ms",
                    methodName, caller, sql, bindParams, summarizeResult(result), cost);
            return result;
        } catch (Throwable e) {
            long cost = System.currentTimeMillis() - start;
            log.warn("[SQL] jdbc.{} | caller={} | sql=[{}] | params=[{}] | error={} | {}ms",
                    methodName, caller, sql, bindParams, e.getMessage(), cost);
            throw e;
        }
    }

    /**
     * 从方法参数中提取 SQL 语句
     */
    private String extractSql(Object[] args) {
        if (args == null || args.length == 0) {
            return "unknown";
        }
        for (Object arg : args) {
            if (arg instanceof String str) {
                String trimmed = str.trim();
                if (isSqlStatement(trimmed)) {
                    return trimmed.length() > MAX_SQL_LENGTH
                            ? trimmed.substring(0, MAX_SQL_LENGTH) + "..."
                            : trimmed;
                }
            }
        }
        return "unknown";
    }

    /**
     * 提取绑定参数，排除 SQL 语句和 Spring 框架对象
     */
    private String extractBindParams(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        int sqlIndex = findSqlIndex(args);
        List<String> paramList = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (i == sqlIndex) {
                continue;
            }
            Object arg = args[i];
            if (isFrameworkObject(arg)) {
                continue;
            }
            paramList.add(formatValue(arg));
        }
        return String.join(", ", paramList);
    }

    private int findSqlIndex(Object[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof String str && isSqlStatement(str.trim())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 判断字符串是否为 SQL 语句
     */
    private boolean isSqlStatement(String str) {
        if (str.length() < 4) {
            return false;
        }
        String upper = str.substring(0, Math.min(7, str.length())).toUpperCase();
        return upper.startsWith("SELECT") || upper.startsWith("INSERT")
                || upper.startsWith("UPDATE") || upper.startsWith("DELETE")
                || upper.startsWith("CREATE") || upper.startsWith("ALTER")
                || upper.startsWith("DROP") || upper.startsWith("TRUNCAT")
                || upper.startsWith("MERGE") || upper.startsWith("CALL")
                || upper.startsWith("REPLACE");
    }

    /**
     * 判断是否为 Spring JDBC 框架对象（不需要展示的参数）
     */
    private boolean isFrameworkObject(Object arg) {
        if (arg == null) {
            return true;
        }
        String className = arg.getClass().getName();
        return className.startsWith("org.springframework.jdbc.core.RowMapper")
                || className.startsWith("org.springframework.jdbc.core.ResultSetExtractor")
                || className.startsWith("org.springframework.jdbc.core.RowCallbackHandler")
                || className.startsWith("org.springframework.jdbc.core.PreparedStatementSetter")
                || className.startsWith("org.springframework.jdbc.core.PreparedStatementCallback")
                || className.startsWith("org.springframework.jdbc.core.BatchPreparedStatementSetter")
                || className.startsWith("org.springframework.jdbc.core.CallableStatementCallback")
                || className.startsWith("org.springframework.jdbc.core.CallableStatementCreator")
                || className.startsWith("org.springframework.jdbc.core.PreparedStatementCreator")
                || className.startsWith("org.springframework.jdbc.core.SqlParameter");
    }

    /**
     * 格式化单个参数值
     */
    private String formatValue(Object arg) {
        if (arg == null) {
            return "null";
        }
        if (arg instanceof Object[] arr) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(formatSingle(arr[i]));
            }
            sb.append("]");
            return sb.toString();
        }
        if (arg instanceof Iterable<?> iterable) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(formatValue(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        return formatSingle(arg);
    }

    /**
     * 格式化单个值
     */
    private String formatSingle(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String s) {
            return "'" + (s.length() > 100 ? s.substring(0, 100) + "..." : s) + "'";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return value.getClass().getSimpleName() + ":" + value;
    }

    /**
     * 摘要化返回结果（避免日志过长）
     */
    private String summarizeResult(Object result) {
        if (result == null) {
            return "null";
        }
        if (result instanceof Integer || result instanceof Long) {
            return "affected=" + result;
        }
        if (result instanceof List<?> list) {
            return "count=" + list.size();
        }
        if (result instanceof int[] arr) {
            return "count=" + arr.length;
        }
        if (result instanceof Boolean b) {
            return b.toString();
        }
        String s = result.toString();
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    /**
     * 从调用栈中解析业务调用方方法
     */
    private String resolveCaller() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        boolean foundJdbc = false;
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            // 跳过切面/代理/框架自身
            if (className.startsWith("org.springframework.jdbc.core")
                    || className.startsWith("com.liang.xz.common.core.log")
                    || className.startsWith("org.springframework.aop")
                    || className.startsWith("org.springframework.cglib")
                    || className.startsWith("jdk.proxy")
                    || className.startsWith("java.lang.Thread")) {
                if (className.startsWith("org.springframework.jdbc.core")) {
                    foundJdbc = true;
                }
                continue;
            }
            if (foundJdbc && className.startsWith("com.liang.xz")) {
                String simpleName = className.substring(className.lastIndexOf('.') + 1);
                return simpleName + "." + element.getMethodName()
                        + "(" + element.getFileName() + ":" + element.getLineNumber() + ")";
            }
        }
        // 兜底：取调用栈上第一个业务层
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            if (className.startsWith("com.liang.xz")
                    && !className.startsWith("com.liang.xz.common.core.log")) {
                String simpleName = className.substring(className.lastIndexOf('.') + 1);
                return simpleName + "." + element.getMethodName()
                        + "(" + element.getFileName() + ":" + element.getLineNumber() + ")";
            }
        }
        return "unknown";
    }
}
