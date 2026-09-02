package com.liang.xz.aiagent.vector;

import com.google.gson.JsonObject;
import com.liang.xz.aiagent.config.AiProperties;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.GetCollectionStatsReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>Milvus 向量存储 — 基于 Milvus 向量数据库的高性能向量检索方案</p>
 *
 * <p>对比 MEMORY / MYSQL 模式的优势：</p>
 * <ul>
 *   <li>支持十亿级向量检索，具备水平扩展能力</li>
 *   <li>HNSW / IVF_FLAT 等专业索引，亚毫秒级检索延迟</li>
 *   <li>标量过滤 + 向量检索混合查询，性能远超应用层余弦计算</li>
 *   <li>数据持久化自动管理，无需手动 reload</li>
 * </ul>
 *
 * <pre>
 * 前置条件: Docker 启动 Milvus Standalone
 *   docker run -d --name milvus-standalone \
 *     -p 19530:19530 -p 9091:9091 \
 *     milvusdb/milvus:v2.4.0
 *
 * 切换方式:
 *   ai-agent.vector.type=MILVUS
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
public class MilvusVectorStore implements VectorStore {

    private static final String FIELD_ID = "id";
    private static final String FIELD_EMBEDDING = "embedding";
    private static final String FIELD_TEXT = "text";
    private static final String FIELD_KB_NAME = "kb_name";
    private static final String FIELD_DOC_ID = "doc_id";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_CHUNK_INDEX = "chunk_index";

    private static final List<String> OUTPUT_FIELDS = List.of(
            FIELD_ID, FIELD_TEXT, FIELD_KB_NAME, FIELD_DOC_ID, FIELD_TITLE);

    private final MilvusClientV2 client;
    private final String collectionName;
    private final int dimensions;
    private final AiProperties.VectorStoreConfig vectorConfig;
    private final AiProperties.MilvusConfig milvusConfig;
    private volatile boolean collectionLoaded;

    public MilvusVectorStore(AiProperties aiProperties) {
        this.vectorConfig = aiProperties.getVector();
        this.milvusConfig = vectorConfig.getMilvus();
        this.collectionName = milvusConfig.getCollectionName();
        this.dimensions = aiProperties.getEmbedding().getDimensions();

        ConnectConfig connectConfig = ConnectConfig.builder()
                .uri("http://" + milvusConfig.getHost() + ":" + milvusConfig.getPort())
                .connectTimeoutMs(milvusConfig.getConnectTimeout() * 1000L)
                .build();
        this.client = new MilvusClientV2(connectConfig);
        this.collectionLoaded = false;

        initCollection();
    }

    // ======================== 集合初始化 ========================

    private void initCollection() {
        try {
            boolean exists = client.hasCollection(HasCollectionReq.builder()
                    .collectionName(collectionName).build());

            if (exists) {
                handleExistingCollection();
            } else {
                createCollectionAndIndex();
            }

            loadCollection();
            log.info("MilvusVectorStore initialized: collection={}, dimensions={}, indexType={}",
                    collectionName, dimensions, milvusConfig.getIndexType());
        } catch (Exception e) {
            log.error("Failed to initialize Milvus collection: {}", collectionName, e);
            throw new RuntimeException("Milvus collection init failed: " + collectionName, e);
        }
    }

    private void handleExistingCollection() {
        if ("DROP".equalsIgnoreCase(milvusConfig.getCollectionExistStrategy())) {
            log.info("Dropping existing Milvus collection: {}", collectionName);
            client.dropCollection(DropCollectionReq.builder().collectionName(collectionName).build());
            createCollectionAndIndex();
        } else {
            log.info("Reusing existing Milvus collection: {} (strategy=SKIP)", collectionName);
        }
    }

    private void createCollectionAndIndex() {
        // 1) 定义 Schema
        CreateCollectionReq.CollectionSchema schema = client.createSchema();
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_ID).dataType(DataType.VarChar)
                .isPrimaryKey(true).maxLength(128).build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_EMBEDDING).dataType(DataType.FloatVector)
                .dimension(dimensions).build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_TEXT).dataType(DataType.VarChar)
                .maxLength(8192).build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_KB_NAME).dataType(DataType.VarChar)
                .maxLength(128).build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_DOC_ID).dataType(DataType.VarChar)
                .maxLength(128).build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_TITLE).dataType(DataType.VarChar)
                .maxLength(512).build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_CHUNK_INDEX).dataType(DataType.Int64).build());

        // 2) 创建 Collection
        long start = System.currentTimeMillis();
        client.createCollection(CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .build());
        log.info("Milvus collection created: {} ({}ms)", collectionName, System.currentTimeMillis() - start);

        // 3) 建索引
        createIndex();
    }

    private void createIndex() {
        IndexParam indexParam = IndexParam.builder()
                .fieldName(FIELD_EMBEDDING)
                .indexType(IndexParam.IndexType.valueOf(milvusConfig.getIndexType()))
                .metricType(IndexParam.MetricType.valueOf(milvusConfig.getMetricType()))
                .extraParams(buildIndexExtraParams())
                .build();

        client.createIndex(CreateIndexReq.builder()
                .collectionName(collectionName)
                .indexParams(Collections.singletonList(indexParam))
                .build());
        log.info("Milvus index created: collection={}, indexType={}, metricType={}",
                collectionName, milvusConfig.getIndexType(), milvusConfig.getMetricType());
    }

    private Map<String, Object> buildIndexExtraParams() {
        Map<String, Object> params = new HashMap<>();
        String indexType = milvusConfig.getIndexType();
        if ("IVF_FLAT".equals(indexType) || "IVF_SQ8".equals(indexType) || "IVF_PQ".equals(indexType)) {
            params.put("nlist", milvusConfig.getNlist());
        } else if ("HNSW".equals(indexType)) {
            params.put("M", milvusConfig.getHnswM());
            params.put("efConstruction", milvusConfig.getHnswEfConstruction());
        }
        return params;
    }

    private void loadCollection() {
        client.loadCollection(LoadCollectionReq.builder()
                .collectionName(collectionName).build());
        collectionLoaded = true;
        log.info("Milvus collection loaded into memory: {}", collectionName);
    }

    private void ensureLoaded() {
        if (!collectionLoaded) {
            synchronized (this) {
                if (!collectionLoaded) {
                    loadCollection();
                }
            }
        }
    }

    // ======================== VectorStore 接口实现 ========================

    @Override
    public void add(VectorDocument doc) {
        if (doc.getEmbedding() == null || doc.getEmbedding().isEmpty()) {
            log.warn("Skip document with empty embedding: {}", doc.getId());
            return;
        }
        addAll(Collections.singletonList(doc));
    }

    @Override
    public void addAll(List<VectorDocument> docs) {
        if (docs.isEmpty()) {
            return;
        }

        List<JsonObject> rows = new ArrayList<>(docs.size());
        for (VectorDocument doc : docs) {
            JsonObject row = new JsonObject();
            row.addProperty(FIELD_ID, doc.getId());
            row.add(FIELD_EMBEDDING, toGsonFloatArray(doc.getEmbedding()));
            row.addProperty(FIELD_TEXT, nullToEmpty(doc.getText()));
            row.addProperty(FIELD_KB_NAME, getMeta(doc, "kbName"));
            row.addProperty(FIELD_DOC_ID, getMeta(doc, "docId"));
            row.addProperty(FIELD_TITLE, getMeta(doc, "title"));
            row.addProperty(FIELD_CHUNK_INDEX, parseChunkIndex(doc));
            rows.add(row);
        }

        try {
            InsertResp insertResp = client.insert(InsertReq.builder()
                    .collectionName(collectionName)
                    .data(rows)
                    .build());
            log.debug("Milvus inserted {} docs, insertCount={}", docs.size(), insertResp.getInsertCnt());
        } catch (Exception e) {
            log.error("Milvus insert failed: {} docs", docs.size(), e);
        }
    }

    @Override
    public List<VectorDocument> search(List<Double> queryEmbedding, int topK) {
        return search(queryEmbedding, topK, vectorConfig.getSimilarityThreshold());
    }

    @Override
    public List<VectorDocument> search(List<Double> queryEmbedding, int topK, double minSimilarity) {
        if (queryEmbedding == null || queryEmbedding.isEmpty()) {
            return List.of();
        }
        ensureLoaded();

        try {
            SearchReq searchReq = SearchReq.builder()
                    .collectionName(collectionName)
                    .annsField(FIELD_EMBEDDING)
                    .data(Collections.singletonList(new FloatVec(toFloatList(queryEmbedding))))
                    .topK(topK)
                    .outputFields(OUTPUT_FIELDS)
                    .searchParams(buildSearchParams())
                    .build();

            SearchResp searchResp = client.search(searchReq);
            List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();

            if (searchResults == null || searchResults.isEmpty()) {
                return List.of();
            }

            return searchResults.get(0).stream()
                    .filter(r -> toSimilarity(r.getScore(), milvusConfig.getMetricType()) >= minSimilarity)
                    .map(this::toVectorDocument)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Milvus search failed", e);
            return List.of();
        }
    }

    @Override
    public void delete(String id) {
        try {
            DeleteResp resp = client.delete(DeleteReq.builder()
                    .collectionName(collectionName)
                    .filter(FIELD_ID + " == \"" + id + "\"")
                    .build());
            log.debug("Milvus deleted doc: id={}, count={}", id, resp.getDeleteCnt());
        } catch (Exception e) {
            log.error("Milvus delete failed: id={}", id, e);
        }
    }

    @Override
    public void deleteByKbName(String kbName) {
        try {
            DeleteResp resp = client.delete(DeleteReq.builder()
                    .collectionName(collectionName)
                    .filter(FIELD_KB_NAME + " == \"" + kbName + "\"")
                    .build());
            log.info("Milvus deleted by kbName={}, count={}", kbName, resp.getDeleteCnt());
        } catch (Exception e) {
            log.error("Milvus deleteByKbName failed: kbName={}", kbName, e);
        }
    }

    @Override
    public void deleteByDocId(String docId) {
        try {
            DeleteResp resp = client.delete(DeleteReq.builder()
                    .collectionName(collectionName)
                    .filter(FIELD_DOC_ID + " == \"" + docId + "\"")
                    .build());
            log.info("Milvus deleted by docId={}, count={}", docId, resp.getDeleteCnt());
        } catch (Exception e) {
            log.error("Milvus deleteByDocId failed: docId={}", docId, e);
        }
    }

    @Override
    public int size() {
        try {
            Long numOfEntities = client.getCollectionStats(GetCollectionStatsReq.builder()
                    .collectionName(collectionName).build()).getNumOfEntities();
            return numOfEntities != null ? numOfEntities.intValue() : 0;
        } catch (Exception e) {
            log.warn("Milvus size query failed", e);
            return -1;
        }
    }

    @Override
    public void clear() {
        try {
            client.dropCollection(DropCollectionReq.builder().collectionName(collectionName).build());
            createCollectionAndIndex();
            loadCollection();
            log.info("Milvus collection cleared and recreated: {}", collectionName);
        } catch (Exception e) {
            log.error("Milvus clear failed", e);
        }
    }

    // ======================== 辅助方法 ========================

    private Map<String, Object> buildSearchParams() {
        Map<String, Object> params = new HashMap<>();
        String indexType = milvusConfig.getIndexType();
        if ("IVF_FLAT".equals(indexType) || "IVF_SQ8".equals(indexType) || "IVF_PQ".equals(indexType)) {
            params.put("nprobe", milvusConfig.getSearchNprobe());
        } else if ("HNSW".equals(indexType)) {
            params.put("ef", milvusConfig.getSearchNprobe() * 2);
        }
        return params;
    }

    private VectorDocument toVectorDocument(SearchResp.SearchResult result) {
        Map<String, Object> entity = result.getEntity();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("kbName", String.valueOf(entity.getOrDefault(FIELD_KB_NAME, "")));
        metadata.put("docId", String.valueOf(entity.getOrDefault(FIELD_DOC_ID, "")));
        metadata.put("title", String.valueOf(entity.getOrDefault(FIELD_TITLE, "")));

        double similarity = toSimilarity(result.getScore(), milvusConfig.getMetricType());

        return VectorDocument.builder()
                .id(String.valueOf(result.getId()))
                .text(String.valueOf(entity.getOrDefault(FIELD_TEXT, "")))
                .metadata(metadata)
                .score(similarity)
                .build();
    }

    /**
     * 将 Milvus 返回的 score 统一转换为余弦相似度 (0~1)
     * <ul>
     *   <li>COSINE: Milvus 直接返回相似度(-1~1)，负值截断为 0</li>
     *   <li>IP: 内积越大越相似，不再做归一化，直接截断</li>
     *   <li>L2: 距离越小越相似，similarity = 1/(1+distance)</li>
     * </ul>
     */
    static double toSimilarity(Float score, String metricType) {
        if (score == null) {
            return 0.0;
        }
        double s = score.doubleValue();
        if ("COSINE".equalsIgnoreCase(metricType)) {
            return Math.max(0.0, s);
        } else if ("IP".equalsIgnoreCase(metricType)) {
            return Math.max(0.0, s);
        } else {
            // L2: 距离越小越相似
            return 1.0 / (1.0 + s);
        }
    }

    // ======================== 静态工具方法 ========================

    private static List<Float> toFloatList(List<Double> doubles) {
        List<Float> floats = new ArrayList<>(doubles.size());
        for (Double d : doubles) {
            floats.add(d.floatValue());
        }
        return floats;
    }

    /**
     * 将 List&lt;Double&gt; embedding 转为 Gson 的 JsonArray，适配 InsertReq 的 Gson 类型要求
     */
    private static com.google.gson.JsonArray toGsonFloatArray(List<Double> doubles) {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (Double d : doubles) {
            array.add(d.floatValue());
        }
        return array;
    }

    private static String getMeta(VectorDocument doc, String key) {
        if (doc.getMetadata() == null) {
            return "";
        }
        return doc.getMetadata().getOrDefault(key, "").toString();
    }

    private static long parseChunkIndex(VectorDocument doc) {
        if (doc.getMetadata() == null) {
            return 0L;
        }
        try {
            return Long.parseLong((String) doc.getMetadata().getOrDefault("chunkIndex", "0"));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
