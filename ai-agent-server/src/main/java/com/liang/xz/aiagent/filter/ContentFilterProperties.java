package com.liang.xz.aiagent.filter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>内容过滤配置属性</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai-agent.content-filter")
public class ContentFilterProperties {

    /** 是否启用内容过滤 */
    private boolean enabled = true;

    /** 自定义敏感词正则列表（可选） */
    private List<String> keywords = new ArrayList<>();
}
