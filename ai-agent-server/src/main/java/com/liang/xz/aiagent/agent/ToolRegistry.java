package com.liang.xz.aiagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>智能体工具注册表 — 以"工具类"为中心管理工具。</p>
 *
 * <p>每个工具类（如 {@link AgentTools}、{@link DataQueryTool}）内部持有带 {@code @Tool} 注解的方法，
 * 并自行管理调用次数、限流等业务逻辑。本注册表直接持有这些工具类实例，
 * 提取其 {@code @Tool} 方法生成 {@link ToolSpecification} 供模型识别，同时保留方法到实例的映射用于反射执行。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry {

    private final AgentTools agentTools;
    private final DataQueryTool dataQueryTool;

    private final ObjectMapper toolObjectMapper = new ObjectMapper();
    private final List<ToolSpecification> toolSpecifications = new ArrayList<>();
    private final Map<String, ToolInvoker> invokers = new LinkedHashMap<>();

    /**
     * 在构造完成后完成工具注册。
     */
    @jakarta.annotation.PostConstruct
    public void register() {
        List<Object> toolInstances = new ArrayList<>();
        toolInstances.add(agentTools);
        toolInstances.add(dataQueryTool);
        for (Object bean : toolInstances) {
            Class<?> userClass = bean.getClass();
            List<Method> toolMethods = new ArrayList<>();
            for (Method method : userClass.getMethods()) {
                if (method.isAnnotationPresent(Tool.class)) {
                    toolMethods.add(method);
                }
            }
            if (toolMethods.isEmpty()) {
                continue;
            }
            for (Method method : toolMethods) {
                String toolName = method.getName();
                invokers.put(toolName, new ToolInvoker(bean, method));
                log.debug("[ToolRegistry] 发现工具方法 {}#{}()", userClass.getSimpleName(), toolName);
            }
        }
        if (!toolInstances.isEmpty()) {
            for (Object toolClass : toolInstances.toArray()) {
                toolSpecifications.addAll(ToolSpecifications.toolSpecificationsFrom(toolClass));
            }
        }
        log.info("[ToolRegistry] 已登记 {} 个工具类: {}", toolInstances.size(),
                toolInstances.stream().map(o -> o.getClass().getSimpleName()).toList());
    }

    /**
     * 返回所有 {@code @Tool} 方法对应的工具规格，直接传给模型用于 function calling。
     *
     * @return 工具规格列表
     */
    public List<ToolSpecification> allToolSpecifications() {
        return Collections.unmodifiableList(toolSpecifications);
    }

    /**
     * 根据工具请求执行对应的 {@code @Tool} 方法。
     *
     * <p>支持任意数量参数。参数名按编译期保留名（或 {@code arg0, arg1} 回退）
     * 从模型返回的 JSON 参数对象中提取并反序列化。</p>
     *
     * @param request 工具执行请求
     * @return 工具执行结果文本
     */
    public String execute(ToolExecutionRequest request) {
        if (request == null || request.name() == null) {
            return "工具请求为空";
        }
        ToolInvoker invoker = invokers.get(request.name());
        if (invoker == null) {
            return "未找到工具: " + request.name();
        }
        Method method = invoker.method();
        Object bean = invoker.bean();
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];
        try {
            if (params.length > 0) {
                JsonNode root = toolObjectMapper.readTree(request.arguments());
                for (int i = 0; i < params.length; i++) {
                    String paramName = resolveParameterName(params, i);
                    JsonNode valueNode = root.get(paramName);
                    args[i] = toolObjectMapper.convertValue(valueNode, params[i].getType());
                }
            }
            Object result = method.invoke(bean, args);
            return result != null ? result.toString() : "";
        } catch (Exception e) {
            log.error("[ToolRegistry] 工具 {} 反射执行失败, arguments={}",
                    request.name(), request.arguments(), e);
            return "工具执行失败: " + e.getMessage();
        }
    }

    /**
     * 解析参数名。若编译时未保留参数名（{@code -parameters}），则使用 {@code arg0, arg1} 回退。
     *
     * <p>LangChain4j 生成工具规格时也使用相同规则，因此模型调用时的 JSON 键与这里一致。</p>
     */
    private String resolveParameterName(Parameter[] params, int index) {
        String name = params[index].getName();
        if (name == null || name.isEmpty() || "arg".equals(name)) {
            return "arg" + index;
        }
        return name;
    }

    /**
     * 工具方法执行器，持有 Spring Bean 实例及其 {@code @Tool} 方法。
     */
    private record ToolInvoker(Object bean, Method method) {
    }
}
