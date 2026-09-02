package com.liang.xz.aiagent.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>单次 Agent 调用的工具调用记录上下文</p>
 * <p>通过 ThreadLocal 在同一线程内收集 LangChain4j 工具执行记录</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public class ToolInvocationContext {

    private static final ThreadLocal<List<String>> TOOLS_USED = ThreadLocal.withInitial(ArrayList::new);

    private static final ThreadLocal<String> SESSION_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> QUESTION = new ThreadLocal<>();

    public static void start(String sessionId, String question) {
        TOOLS_USED.remove();
        SESSION_ID.set(sessionId);
        QUESTION.set(question);
    }

    public static void record(String toolName) {
        if (toolName == null || toolName.isBlank()) return;
        List<String> list = TOOLS_USED.get();
        if (!list.contains(toolName)) {
            list.add(toolName);
        }
    }

    public static List<String> getUsedTools() {
        return Collections.unmodifiableList(new ArrayList<>(TOOLS_USED.get()));
    }

    public static String getSessionId() {
        return SESSION_ID.get();
    }

    public static String getQuestion() {
        return QUESTION.get();
    }

    public static void clear() {
        TOOLS_USED.remove();
        SESSION_ID.remove();
        QUESTION.remove();
    }
}
