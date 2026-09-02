package com.liang.xz.aiagent.config;

import com.liang.xz.aiagent.search.LocalSearchEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p>Lucene 本地搜索引擎配置</p>
 * <p>可通过 ai-agent.local-search.enabled=false 禁用本地搜索</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class LuceneConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "ai-agent.local-search", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LocalSearchEngine localSearchEngine(AiProperties aiProperties) {
        try {
            return new LocalSearchEngine(aiProperties.getLocalSearch());
        } catch (RuntimeException e) {
            log.error("Lucene搜索引擎初始化失败，本地搜索功能将不可用: {}", e.getMessage());
            // 返回一个"不可用"的实例(通过配置禁用即可避免此错误)
            throw e;
        }
    }
}
