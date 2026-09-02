package com.liang.xz.aiagent.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>AI 智能体统一配置属性</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "ai-agent")
public class AiProperties {

    /**
     * Embedding 默认配置。
     * 说明：LLM 供应商与路由均已由页面配置（数据库 sys_ai_provider 表）管理，
     * 不再从配置文件中读取；仅嵌入模型保留一份"默认兜底配置"，
     * 当数据库中未配置嵌入供应商（providerCode 指向的供应商不存在）时作为后备使用。
     */
    private EmbeddingConfig embedding = new EmbeddingConfig();

    /** 向量存储配置 */
    private VectorStoreConfig vector = new VectorStoreConfig();

    /** 检索阶段配置（宽召回倍数等） */
    private SearchConfig search = new SearchConfig();

    /** 重排序（Rerank）模型配置 */
    private RerankConfig rerank = new RerankConfig();

    /** 本地搜索引擎配置 */
    private LocalSearchConfig localSearch = new LocalSearchConfig();

    /** 联网搜索配置 */
    private WebSearchConfig webSearch = new WebSearchConfig();

    /** 分析预警配置 */
    private AnalysisConfig analysis = new AnalysisConfig();

    /** 模型路由配置 */
    private ModelRoutingConfig modelRouting = new ModelRoutingConfig();

    /** 文件上传配置 */
    private FileUploadConfig fileUpload = new FileUploadConfig();

    /** 响应缓存配置 */
    private CacheConfig cache = new CacheConfig();

    /** 会话管理配置 */
    private SessionConfig session = new SessionConfig();

    /** 工作流编排配置 */
    private WorkflowConfig workflow = new WorkflowConfig();

    /** 多Agent编排配置 */
    private OrchestrationConfig orchestration = new OrchestrationConfig();

    /** 文档深度分析配置（PDF/Word 的元素识别、表格还原、图片与扫描件视觉识别） */
    private com.liang.xz.aiagent.service.document.model.DocumentAnalysisProperties documentAnalysis =
            new com.liang.xz.aiagent.service.document.model.DocumentAnalysisProperties();

    /** 复杂度路由配置 */
    private ComplexityRoutingConfig complexityRouting = new ComplexityRoutingConfig();

    @Data
    public static class EmbeddingConfig {
        /**
         * 嵌入供应商编码，指向数据库 sys_ai_provider 表中的某条启用供应商。
         * 业务优先使用该供应商的 embedding 配置；若此编码对应的供应商不存在或不可用，
         * 则回退到下方 standalone 默认配置。
         */
        private String providerCode = "";
        /**
         * 默认嵌入模型兜底配置：当数据库中未配置嵌入供应商时使用。
         * 兼容 OpenAI /v1/embeddings 协议。
         */
        private boolean standalone = false;
        /** 默认嵌入 API 地址，仅数据库无嵌入供应商时生效 */
        private String baseUrl = "";
        /** 默认嵌入 API Key，仅数据库无嵌入供应商时生效 */
        private String apiKey = "";
        /** 默认嵌入模型 */
        private String model = "text-embedding-ada-002";
        /** 向量维度 */
        private int dimensions = 1536;
        /** 批量大小 */
        private int batchSize = 100;
    }

    @Data
    public static class VectorStoreConfig {
        /** 存储类型: MEMORY / MYSQL / MILVUS / PGVECTOR / REDIS */
        private String type = "MEMORY";
        /** Redis Key 前缀(type=REDIS时使用) */
        private String redisKeyPrefix = "ai:vector:";
        /** 检索返回的 Top-K */
        private int topK = 5;
        /** 相似度阈值(低于此值的结果过滤掉) */
        private double similarityThreshold = 0.5;
        /** Milvus 配置 */
        private MilvusConfig milvus = new MilvusConfig();
    }

    @Data
    public static class SearchConfig {
        /**
         * 宽召回倍数：向量检索阶段先召回 {@code topK * retrieveTopK} 个候选，
         * 再交给重排序模型精排取最终 topK，提升召回质量。
         */
        private int retrieveTopK = 3;
    }

    @Data
    public static class RerankConfig {
        /** 是否启用重排序模型（RAG 检索的最终排序阶段） */
        private boolean enabled = true;
        /** 重排序服务地址 */
        private String url = "http://127.0.0.1:8000/rerank";
        /** 重排序模型名 */
        private String model = "bge-reranker-base";
        /** 连接超时(秒) */
        private int connectTimeout = 10;
        /** 读超时(秒) */
        private int readTimeout = 30;
        /** 单次重排序请求的最大候选文档数 */
        private int maxCandidates = 50;
    }

    @Data
    public static class MilvusConfig {
        /** Milvus 服务地址 */
        private String host = "localhost";
        /** Milvus gRPC 端口 */
        private int port = 19530;
        /** 集合名称 */
        private String collectionName = "ai_knowledge_vectors";
        /** 索引类型: IVF_FLAT / IVF_SQ8 / HNSW / IVF_PQ */
        private String indexType = "IVF_FLAT";
        /** 索引度量类型: COSINE / L2 / IP */
        private String metricType = "COSINE";
        /** IVF 聚类数(nlist)，值越大精度越高但速度越慢 */
        private int nlist = 1024;
        /** HNSW 图的出度(M)，值越大精度越高但内存占用越多 */
        private int hnswM = 16;
        /** HNSW 构建时搜索宽度 */
        private int hnswEfConstruction = 200;
        /** 搜索参数 ef (HNSW) 或 nprobe (IVF)，值越大精度越高 */
        private int searchNprobe = 16;
        /** 连接超时(秒) */
        private int connectTimeout = 10;
        /** 当集合已存在时的处理策略: DROP (删除重建) / SKIP (保留原有) */
        private String collectionExistStrategy = "SKIP";
    }

    @Data
    public static class LocalSearchConfig {
        /** Lucene 索引存储路径 */
        private String indexPath = "./data/lucene-index";
        /** 默认最大返回数 */
        private int maxResults = 20;
        /** 高亮片段长度 */
        private int fragmentSize = 200;
        /** 是否启用中文分词(需要额外依赖) */
        private boolean chineseAnalyzer = false;
    }

    @Data
    public static class WebSearchConfig {
        /** 搜索引擎类型: BRAVE / SERPAPI / BING / SEARCHPIN（自托管，免 API Key） */
        private String provider = "BRAVE";
        /** API Key */
        private String apiKey = "";
        /** 默认最大返回数 */
        private int maxResults = 10;
        /** 请求超时(秒) */
        private int timeout = 30;
        /** 各搜索引擎 endpoint */
        private Map<String, String> endpoints = Map.of(
                "BRAVE", "https://api.search.brave.com/res/v1/web/search",
                "SERPAPI", "https://serpapi.com/search",
                "BING", "https://api.bing.microsoft.com/v7.0/search"
        );
        /** Searchpin（MCP 自托管联网搜索）是否启用 */
        private boolean searchpinEnabled = true;
        /** Searchpin 服务启动命令（stdio MCP server） */
        private String searchpinCommand = "searchpin-server";
        /** Searchpin 单次最大结果数（服务端上限 20） */
        private int searchpinMaxResults = 10;
        /** Searchpin 进程启动/调用整体超时(秒，含首次模型下载) */
        private int searchpinTimeout = 120;
    }

    @Data
    public static class AnalysisConfig {
        /** 是否启用自动分析预警 */
        private boolean enabled = true;
        /** 定时分析 Cron 表达式 */
        private String cron = "0 0/30 * * * ?";
        /** 默认分析窗口(天) */
        private int defaultWindowDays = 7;
        /** 预警通知渠道(复用消息模块) */
        private String alertChannel = "IN_APP";
    }

    @Data
    public static class ModelRoutingConfig {
        /** 是否启用自动模型路由 */
        private boolean enabled = false;
        /** 用于分类的轻量模型，留空时回退到数据库供应商默认模型 */
        private String classificationModel = "";
        /** 各类型的路由规则 */
        private Map<String, RouteConfig> routes = Map.of();
    }

    @Data
    public static class RouteConfig {
        /** 所属供应商key(对应providers中的key, 为空则使用默认llm配置) */
        private String provider;
        /** 触发关键词 */
        private List<String> keywords = List.of();
        /** 路由到的模型 */
        private String model = "gpt-3.5-turbo";
        /** 温度 */
        private double temperature = 0.7;
        /** 最大 Token */
        private int maxTokens = 2048;
    }

    // ======================== 辅助方法 ========================

    @Data
    public static class FileUploadConfig {
        /** 最大文件大小 */
        private String maxFileSize = "20MB";
        /** 单次上传允许的最大文件数量 */
        private int maxFileCount = 20;
        /** 单个文件索引(解析+向量化)超时时间(秒)，超时则标记该文件任务失败 */
        private int perFileIndexTimeoutSeconds = 600;
        /** 支持的格式列表 */
        private List<String> supportedFormats = List.of("pdf", "docx", "xlsx", "txt", "md", "html", "htm", "csv", "json",
                "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg",
                "mp3", "wav", "flac", "aac", "ogg",
                "mp4", "avi", "mkv", "mov", "wmv");
    }

    @Data
    public static class CacheConfig {
        /** 是否启用响应缓存 */
        private boolean enabled = true;
        /** 缓存最大条目数 */
        private int maxSize = 500;
        /** 缓存过期时间(分钟) */
        private int ttlMinutes = 30;
        /** 相似度阈值(高于此值视为命中缓存) */
        private double similarityThreshold = 0.92;
    }

    @Data
    public static class SessionConfig {
        /** 会话最大保留条数 */
        private int maxSessions = 50;
        /** 上下文窗口大小(保留最近N条未压缩消息) */
        private int contextWindowSize = 10;
        /** 消息总数达到此阈值时触发压缩 */
        private int compressThreshold = 20;
        /** 会话空闲过期时间(小时)，默认 30 天 */
        private int idleExpireHours = 720;
    }

    /**
     * 多 Agent 编排配置。
     *
     * <p>用于控制"复杂问题是否交由多个专业 Agent 协作完成"。编排链路会额外产生多次
     * LLM 调用（任务规划 + 各子任务执行 + 结果汇总），成本与延迟高于单链路，
     * 因此提供总开关：线上若出现编排异常或成本压力，设 {@code enabled=false} 即可
     * 立即回退到原有的单 Agent 工具链路，无需发版。</p>
     */
    @Data
    public static class OrchestrationConfig {
        /** 是否启用多 Agent 编排 */
        private boolean enabled = true;
    }

    @Data
    public static class WorkflowConfig {
        /** 是否启用工作流编排 */
        private boolean enabled = true;
        /** 子任务并发执行最大数 */
        private int maxConcurrentSubtasks = 4;
        /** 单子任务超时(秒) */
        private int subtaskTimeoutSeconds = 60;
        /** 最大重试次数 */
        private int maxRetries = 2;
    }

    @Data
    public static class ComplexityRoutingConfig {
        /** 是否启用复杂度路由 */
        private boolean enabled = true;
        /** 复杂度路由使用的供应商 (对应 providers 的 key，默认 inputai) */
        private String provider = "inputai";
        /**
         * 各复杂度对应的模型名。留空时复杂度路由不指定具体模型，
         * 由上层回退到数据库配置的供应商默认模型，避免写死不存在的模型 code。
         */
        private String simpleModel = "";
        /** 中等问题使用的模型 */
        private String mediumModel = "";
        /** 复杂问题使用的模型 */
        private String complexModel = "";
        /** 简单问题的最大Token限制 */
        private int simpleMaxTokens = 1024;
        /** 中等问题的最大Token限制 */
        private int mediumMaxTokens = 2048;
        /** 复杂问题的最大Token限制 */
        private int complexMaxTokens = 4096;
    }
}
