package com.liang.xz.aiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>文本分割器 — 支持按文档类型选择切片策略</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Component
public class TextSplitter {

    /** 默认 chunk 大小(字符) */
    public static final int DEFAULT_CHUNK_SIZE = 500;
    /** chunk 重叠字符数(保持上下文连贯) */
    public static final int DEFAULT_OVERLAP = 50;

    /**
     * 切片策略
     */
    public enum SplitStrategy {
        /** 通用段落策略 */
        PARAGRAPH,
        /** Markdown 结构策略 */
        MARKDOWN,
        /** HTML 标签边界策略 */
        HTML,
        /** 代码函数/类边界策略 */
        CODE,
        /** JSON 节点策略 */
        JSON,
        /** CSV 行策略 */
        CSV,
        /** 固定字符策略 */
        CHARACTER
    }

    /**
     * 根据内容类型推断最佳切片策略
     */
    public static SplitStrategy inferStrategy(String contentType) {
        if (contentType == null) {
            return SplitStrategy.PARAGRAPH;
        }
        String ct = contentType.toUpperCase();
        return switch (ct) {
            case "MARKDOWN", "MD" -> SplitStrategy.MARKDOWN;
            case "HTML", "HTM" -> SplitStrategy.HTML;
            case "JSON" -> SplitStrategy.JSON;
            case "CSV" -> SplitStrategy.CSV;
            case "JAVA", "PYTHON", "JS", "TS", "GO", "RUST", "C", "CPP", "SQL", "YAML", "YML",
                 "XML", "PROPERTIES", "SH", "BASH", "PS1" -> SplitStrategy.CODE;
            default -> SplitStrategy.PARAGRAPH;
        };
    }

    /**
     * 按默认策略分割文本
     */
    public List<String> split(String text) {
        return split(text, SplitStrategy.PARAGRAPH, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    /**
     * 按指定策略和参数分割文本
     */
    public List<String> split(String text, SplitStrategy strategy, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (chunkSize <= 0) chunkSize = DEFAULT_CHUNK_SIZE;
        if (overlap < 0) overlap = 0;
        if (overlap >= chunkSize) overlap = chunkSize / 5;

        List<String> chunks = switch (strategy) {
            case MARKDOWN -> splitMarkdown(text, chunkSize, overlap);
            case HTML -> splitHtml(text, chunkSize, overlap);
            case CODE -> splitCode(text, chunkSize, overlap);
            case JSON -> splitJson(text, chunkSize, overlap);
            case CSV -> splitCsv(text, chunkSize, overlap);
            case CHARACTER -> splitCharacter(text, chunkSize, overlap);
            default -> splitParagraph(text, chunkSize, overlap);
        };

        // 兜底：任何策略至少保证一个 chunk
        if (chunks.isEmpty()) {
            chunks.add(text.trim());
        }

        log.debug("Split text into {} chunks (strategy={}, chunkSize={}, overlap={})",
                chunks.size(), strategy, chunkSize, overlap);
        return chunks;
    }

    // ==================== 段落策略 ====================

    private List<String> splitParagraph(String text, int chunkSize, int overlap) {
        String[] paragraphs = text.split("\\n\\s*\\n");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String p : paragraphs) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) continue;

            if (current.length() + trimmed.length() <= chunkSize) {
                if (current.length() > 0) current.append("\n\n");
                current.append(trimmed);
            } else {
                if (current.length() > 0) chunks.add(current.toString());
                if (trimmed.length() > chunkSize) {
                    chunks.addAll(splitSentences(trimmed, chunkSize));
                    current = new StringBuilder();
                } else {
                    current = new StringBuilder(appendOverlap(chunks, trimmed, overlap));
                }
            }
        }
        if (current.length() > 0) chunks.add(current.toString());
        return chunks;
    }

    // ==================== Markdown 策略 ====================

    private List<String> splitMarkdown(String text, int chunkSize, int overlap) {
        // 按标题(# ## ###)和段落边界分割
        String[] sections = text.split("(?=\\n#{1,6}\\s)");
        return mergeSections(Arrays.asList(sections), chunkSize, overlap);
    }

    // ==================== HTML 策略 ====================

    private List<String> splitHtml(String text, int chunkSize, int overlap) {
        // 按常见块级标签边界分割
        String[] sections = text.split("(?=<(?:p|div|section|article|h[1-6]|ul|ol|table|blockquote)[^>]*>)");
        return mergeSections(Arrays.asList(sections), chunkSize, overlap);
    }

    // ==================== JSON 策略 ====================

    private List<String> splitJson(String text, int chunkSize, int overlap) {
        List<String> nodes = new ArrayList<>();
        extractJsonNodes(text, nodes, 0);
        if (nodes.isEmpty()) {
            nodes.add(text);
        }
        return mergeSections(nodes, chunkSize, overlap);
    }

    private int extractJsonNodes(String text, List<String> nodes, int start) {
        int i = start;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '{' || c == '[') {
                int end = findMatchingBrace(text, i, c);
                if (end > i) {
                    nodes.add(text.substring(i, end + 1));
                    i = end + 1;
                    continue;
                }
            }
            i++;
        }
        return i;
    }

    private int findMatchingBrace(String text, int start, char open) {
        char close = open == '{' ? '}' : ']';
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;
            if (c == open) depth++;
            else if (c == close) depth--;
            if (depth == 0) return i;
        }
        return -1;
    }

    // ==================== CSV 策略 ====================

    private List<String> splitCsv(String text, int chunkSize, int overlap) {
        String[] lines = text.split("\\r?\\n");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (current.length() + line.length() + 1 > chunkSize) {
                if (current.length() > 0) chunks.add(current.toString());
                current = new StringBuilder(appendOverlap(chunks, line, overlap));
            } else {
                if (current.length() > 0) current.append("\n");
                current.append(line);
            }
        }
        if (current.length() > 0) chunks.add(current.toString());
        return chunks;
    }

    // ==================== 代码策略 ====================

    private List<String> splitCode(String text, int chunkSize, int overlap) {
        // 按函数/类/方法边界 + 段落边界
        String[] sections = text.split("(?=\\n(?:public|private|protected|static|class|interface|function|def|fn|\\w+\\s+\\w+\\s*\\())");
        return mergeSections(Arrays.asList(sections), chunkSize, overlap);
    }

    // ==================== 固定字符策略 ====================

    private List<String> splitCharacter(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start += chunkSize - overlap;
            if (start >= end) start = end; // 防止死循环
        }
        return chunks;
    }

    // ==================== 通用合并辅助 ====================

    private List<String> mergeSections(List<String> sections, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sec : sections) {
            String trimmed = sec.trim();
            if (trimmed.isEmpty()) continue;
            if (current.length() + trimmed.length() <= chunkSize) {
                if (!current.isEmpty()) current.append("\n\n");
                current.append(trimmed);
            } else {
                if (!current.isEmpty()) chunks.add(current.toString());
                if (trimmed.length() > chunkSize) {
                    chunks.addAll(splitCharacter(trimmed, chunkSize, overlap));
                    current = new StringBuilder();
                } else {
                    current = new StringBuilder(appendOverlap(chunks, trimmed, overlap));
                }
            }
        }
        if (!current.isEmpty()) chunks.add(current.toString());
        return chunks;
    }

    private List<String> splitSentences(String text, int chunkSize) {
        List<String> result = new ArrayList<>();
        String[] sentences = text.split("(?<=[。！？；.!?;])");
        StringBuilder chunk = new StringBuilder();
        for (String sentence : sentences) {
            if (chunk.length() + sentence.length() > chunkSize) {
                if (chunk.length() > 0) result.add(chunk.toString().trim());
                chunk = new StringBuilder(sentence);
            } else {
                chunk.append(sentence);
            }
        }
        if (chunk.length() > 0) result.add(chunk.toString().trim());
        return result;
    }

    private String appendOverlap(List<String> chunks, String newText, int overlap) {
        if (chunks.isEmpty() || overlap <= 0) return newText;
        String last = chunks.get(chunks.size() - 1);
        int start = Math.max(0, last.length() - overlap);
        return last.substring(start) + "\n\n" + newText;
    }
}
