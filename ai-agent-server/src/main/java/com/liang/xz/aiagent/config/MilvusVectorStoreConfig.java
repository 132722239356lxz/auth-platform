package com.liang.xz.aiagent.config;

import com.liang.xz.aiagent.vector.MilvusVectorStore;
import com.liang.xz.aiagent.vector.VectorStore;
import io.milvus.v2.client.MilvusClientV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p>Milvus 向量存储条件装配</p>
 * <p>仅在类路径存在 Milvus SDK 且配置 type=MILVUS 时才生效</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnClass(MilvusClientV2.class)
public class MilvusVectorStoreConfig {

    @Bean
    @ConditionalOnProperty(name = "ai-agent.vector.type", havingValue = "MILVUS")
    public VectorStore milvusVectorStore(AiProperties aiProperties) {
        AiProperties.MilvusConfig milvusConfig = aiProperties.getVector().getMilvus();
        log.info(">>> VectorStore 模式: MILVUS (host={}:{}, collection={}, index={})",
                milvusConfig.getHost(), milvusConfig.getPort(),
                milvusConfig.getCollectionName(), milvusConfig.getIndexType());
        return new MilvusVectorStore(aiProperties);
    }
}
