package com.liang.xz.aiagent.filter;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <p>对话内容安全过滤服务</p>
 *
 * <p>对输入问题进行敏感信息检测，命中规则时返回拒绝原因。过滤规则包括：
 * <ul>
 *   <li>极端暴力、恐怖主义相关内容</li>
 *   <li>色情、性相关低俗内容</li>
 *   <li>毒品、武器、爆炸物等非法活动</li>
 *   <li>网络诈骗、洗钱、赌博等违法金融行为</li>
 *   <li>个人隐私信息（身份证号、手机号、银行卡号等）</li>
 * </ul>
 * 规则支持通过 {@code ai-agent.content-filter.keywords} 配置扩展。
 * </p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class ContentFilterService {

    private static final int MAX_SENSITIVE_KEYWORDS = 3;

    /** 内置敏感词分类（仅包含公开可描述的通用风险类别，不含政治相关） */
    private final Map<String, List<String>> builtinCategories = new LinkedHashMap<>();

    private final ContentFilterProperties properties;
    private List<Pattern> extraPatterns = new ArrayList<>();

    public ContentFilterService(ContentFilterProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        builtinCategories.put("violence", List.of(
                "杀人", "爆炸", "炸弹", "恐袭", "恐怖袭击", "屠杀", "肢解", "虐杀",
                "kill", "bomb", "explosive", "terrorist attack", "massacre"
        ));
        builtinCategories.put("porn", List.of(
                "色情", "裸体", "性爱", "性交", "卖淫", "嫖娼", "强奸", "乱伦",
                "porn", "nude", "sexual intercourse", "prostitution", "rape"
        ));
        builtinCategories.put("illegal", List.of(
                "毒品", "冰毒", "海洛因", "可卡因", "制造枪支", "伪钞", "假币",
                "drug", "meth", "heroin", "cocaine", "counterfeit money", "fake currency"
        ));
        builtinCategories.put("fraud", List.of(
                "诈骗", "洗钱", "赌博", "网络钓鱼", "木马", "盗刷",
                "fraud", "money laundering", "gambling", "phishing", "trojan", "carding"
        ));
        builtinCategories.put("privacy", List.of(
                "身份证", "手机号", "银行卡", "信用卡", "密码", "验证码",
                "ID card", "phone number", "bank card", "credit card", "password", "verification code"
        ));

        if (properties.getKeywords() != null) {
            extraPatterns = properties.getKeywords().stream()
                    .filter(k -> k != null && !k.isBlank())
                    .map(k -> Pattern.compile(k, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
                    .collect(Collectors.toList());
        }

        log.info("[ContentFilter] 已加载 {} 个内置敏感类别, {} 个自定义规则",
                builtinCategories.size(), extraPatterns.size());
    }

    /**
     * 是否启用内容过滤
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 检查文本是否包含敏感信息
     *
     * @param text 待检测文本
     * @return 命中时返回拒绝原因；未命中返回 empty
     */
    public Optional<String> check(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String lower = text.toLowerCase();

        for (Map.Entry<String, List<String>> entry : builtinCategories.entrySet()) {
            List<String> matched = entry.getValue().stream()
                    .filter(k -> lower.contains(k.toLowerCase()))
                    .limit(MAX_SENSITIVE_KEYWORDS)
                    .toList();
            if (!matched.isEmpty()) {
                return Optional.of("内容涉及" + categoryName(entry.getKey()) + "，无法处理");
            }
        }

        for (Pattern pattern : extraPatterns) {
            if (pattern.matcher(text).find()) {
                return Optional.of("内容包含敏感信息，无法处理");
            }
        }

        return Optional.empty();
    }

    private String categoryName(String key) {
        return switch (key) {
            case "violence" -> "暴力恐怖信息";
            case "porn" -> "色情低俗信息";
            case "illegal" -> "非法活动信息";
            case "fraud" -> "违法金融/诈骗信息";
            case "privacy" -> "个人隐私信息";
            default -> "敏感信息";
        };
    }
}
