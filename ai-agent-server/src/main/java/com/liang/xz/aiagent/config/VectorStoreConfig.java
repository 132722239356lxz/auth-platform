package com.liang.xz.aiagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.xz.aiagent.vector.InMemoryVectorStore;
import com.liang.xz.aiagent.vector.MysqlVectorStore;
import com.liang.xz.aiagent.vector.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * <p>向量存储条件装配 — 根据 ai-agent.vector.type 配置切换存储后端</p>
 *
 * <pre>
 * 可选值:
 *   MEMORY    - 内存向量存储（默认），检索最快，适合数据量 < 10万条。进程重启时从 MySQL reload。
 *   MYSQL     - MySQL 纯数据库存储，向量 JSON 序列化存 LONGTEXT 列，检索时实时计算余弦相似度。
 *               适合不想引入额外组件的中小规模场景。
 *   MILVUS    - Milvus 向量数据库，支持十亿级向量检索，HNSW/IVF 专业索引。
 *               适合生产环境大规模 RAG 场景。需先启动 Milvus 服务。
 *   PGVECTOR  - PostgreSQL + pgvector 扩展（预留，待实现）
 *   REDIS     - Redis 向量索引（预留，待实现）
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class VectorStoreConfig {

    /**
     * 内存向量存储 — 默认方案，向量常驻 JVM 堆内存
     */
    @Bean
    @ConditionalOnProperty(name = "ai-agent.vector.type", havingValue = "MEMORY", matchIfMissing = true)
    public VectorStore inMemoryVectorStore(AiProperties aiProperties) {
        log.info(">>> VectorStore 模式: MEMORY (内存余弦相似度检索)");
        return new InMemoryVectorStore(aiProperties);
    }

    /**
     * MySQL 向量存储 — 向量以 JSON 存 MySQL，检索时实时计算
     */
    @Bean
    @ConditionalOnProperty(name = "ai-agent.vector.type", havingValue = "MYSQL")
    public VectorStore mysqlVectorStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                        AiProperties aiProperties) {
        log.info(">>> VectorStore 模式: MYSQL (MySQL 存储 + 内存余弦相似度检索)");
        return new MysqlVectorStore(jdbcTemplate, objectMapper, aiProperties);
    }

}
