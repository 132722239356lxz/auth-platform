package com.liang.xz.aiagent;

import com.liang.xz.aiagent.config.AiProperties;
import com.liang.xz.aiagent.vector.InMemoryVectorStore;
import com.liang.xz.aiagent.vector.VectorDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <p>向量存储 & 检索测试</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@DisplayName("向量存储测试")
class VectorStoreTest {

    private InMemoryVectorStore vectorStore;
    private final Random random = new Random(42);

    @BeforeEach
    void setUp() {
        AiProperties props = new AiProperties();
        props.getVector().setTopK(5);
        props.getVector().setSimilarityThreshold(0.3);
        vectorStore = new InMemoryVectorStore(props);
    }

    @Test
    @DisplayName("添加和检索向量文档")
    void testAddAndSearch() {
        // 准备数据: 5组主题明确的"文档"
        List<String> topics = List.of("OAuth2认证", "JWT令牌", "工作流审批", "消息推送", "数据分析");
        List<List<Double>> embeddings = new ArrayList<>();

        for (int i = 0; i < topics.size(); i++) {
            // 每个主题对应的"原型向量"
            List<Double> emb = new ArrayList<>();
            for (int d = 0; d < 16; d++) {
                emb.add(random.nextDouble());
            }
            // 在某个维度上强化主题特征
            emb.set(i % 16, 0.9 + random.nextDouble() * 0.1);
            embeddings.add(emb);

            VectorDocument doc = VectorDocument.builder()
                    .id("doc_" + i)
                    .text("这是关于" + topics.get(i) + "的文档内容")
                    .embedding(emb)
                    .metadata(Map.of("topic", topics.get(i)))
                    .build();
            vectorStore.add(doc);
        }

        assertEquals(5, vectorStore.size());

        // 用第0个主题的向量检索 → 应该返回相关文档
        List<VectorDocument> results = vectorStore.search(embeddings.get(0), 3);
        assertFalse(results.isEmpty());
        // 第一个结果应该是最匹配的(自身)
        assertEquals("doc_0", results.get(0).getId());
    }

    @Test
    @DisplayName("相似度阈值过滤")
    void testSimilarityThreshold() {
        // 添加两个完全不相关的向量
        List<Double> emb1 = List.of(1.0, 0.0, 0.0, 0.0);
        List<Double> emb2 = List.of(0.0, 0.0, 1.0, 0.0);

        vectorStore.add(VectorDocument.builder().id("d1").text("text1").embedding(emb1).build());
        vectorStore.add(VectorDocument.builder().id("d2").text("text2").embedding(emb2).build());

        // emb1 和 emb2 的点积为0 → 余弦相似度为0
        List<VectorDocument> results = vectorStore.search(emb2, 5);
        // d2自己应该匹配, d1不应该(相似度0 < threshold 0.3)
        assertEquals(1, results.size());
        assertEquals("d2", results.get(0).getId());
    }

    @Test
    @DisplayName("按知识库删除")
    void testDeleteByKbName() {
        for (int i = 0; i < 3; i++) {
            vectorStore.add(VectorDocument.builder()
                    .id("kb1_" + i)
                    .text("kb1 doc")
                    .embedding(List.of(1.0, 0.0, 0.0))
                    .metadata(Map.of("kbName", "kb1"))
                    .build());
        }
        for (int i = 0; i < 2; i++) {
            vectorStore.add(VectorDocument.builder()
                    .id("kb2_" + i)
                    .text("kb2 doc")
                    .embedding(List.of(0.0, 1.0, 0.0))
                    .metadata(Map.of("kbName", "kb2"))
                    .build());
        }

        assertEquals(5, vectorStore.size());

        vectorStore.deleteByKbName("kb1");
        assertEquals(2, vectorStore.size());

        // 验证剩余的文档都属于kb2
        List<VectorDocument> results = vectorStore.search(List.of(0.0, 1.0, 0.0), 10);
        assertEquals(2, results.size());
        for (var doc : results) {
            assertNotNull(doc.getMetadata());
            assertNotEquals("kb1", doc.getMetadata().get("kbName"));
        }
    }

    @Test
    @DisplayName("余弦相似度计算正确性")
    void testCosineSimilarity() {
        List<Double> a = List.of(1.0, 0.0);
        List<Double> b = List.of(0.0, 1.0);
        List<Double> c = List.of(0.5, 0.5);

        // 正交向量 → 相似度=0
        vectorStore.add(VectorDocument.builder().id("a").text("").embedding(a).build());
        vectorStore.add(VectorDocument.builder().id("b").text("").embedding(b).build());
        vectorStore.add(VectorDocument.builder().id("c").text("").embedding(c).build());

        // a自身 → 相似度=1.0
        List<VectorDocument> self = vectorStore.search(a, 1);
        assertEquals("a", self.get(0).getId());
        assertTrue(self.get(0).getScore() > 0.99);

        // a和c → 有相似度
        List<VectorDocument> ac = vectorStore.search(a, 3);
        assertTrue(ac.stream().anyMatch(d -> "c".equals(d.getId())));
    }
}
