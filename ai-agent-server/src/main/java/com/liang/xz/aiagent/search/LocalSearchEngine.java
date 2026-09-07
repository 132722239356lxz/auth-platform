package com.liang.xz.aiagent.search;

import com.liang.xz.aiagent.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;

import org.apache.lucene.index.IndexableField;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * <p>本地搜索引擎 — 基于 Apache Lucene 的离线全文检索</p>
 * <p>能力: 索引构建 / 多字段检索 / 高亮 / 增量更新</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
public class LocalSearchEngine implements AutoCloseable {

    private final FSDirectory directory;
    private final StandardAnalyzer analyzer;
    private final AiProperties.LocalSearchConfig config;
    private IndexWriter indexWriter;

    public LocalSearchEngine(AiProperties.LocalSearchConfig config) {
        this.config = config;
        this.analyzer = new StandardAnalyzer();
        try {
            Path indexPath = Path.of(config.getIndexPath());
            Files.createDirectories(indexPath);
            this.directory = FSDirectory.open(indexPath);
            this.indexWriter = createWriter();
            log.info("Lucene index initialized at: {}", indexPath.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize Lucene index at " + config.getIndexPath(), e);
        }
    }

    private IndexWriter createWriter() throws IOException {
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        try {
            return new IndexWriter(directory, iwc);
        } catch (LockObtainFailedException e) {
            log.warn("Lucene write.lock held by stale process, attempting to force unlock...");
            Path lockPath = Path.of(config.getIndexPath(), "write.lock");
            Files.deleteIfExists(lockPath);
            log.info("Cleared stale write.lock: {}", lockPath.toAbsolutePath());
            return new IndexWriter(directory, iwc);
        }
    }

    // ===================== 索引操作 =====================

    /**
     * 索引单个文档
     */
    public void index(String id, String title, String content, Map<String, String> metadata) {
        try {
            Document doc = new Document();
            doc.add(new StringField("id", id, Field.Store.YES));
            doc.add(new TextField("title", title, Field.Store.YES));
            doc.add(new TextField("content", content, Field.Store.YES));

            if (metadata != null) {
                metadata.forEach((k, v) ->
                        doc.add(new StringField("meta_" + k, v, Field.Store.YES)));
            }
            doc.add(new StoredField("contentLength", content.length()));
            doc.add(new StringField("_indexed", "true", Field.Store.NO));

            indexWriter.updateDocument(new Term("id", id), doc);
            indexWriter.commit();
            log.debug("Indexed document: {}", id);
        } catch (IOException e) {
            log.error("Failed to index document: {}", id, e);
        }
    }

    /**
     * 批量索引
     */
    public void indexBatch(List<DocumentEntry> entries) {
        try {
            for (DocumentEntry entry : entries) {
                Document doc = new Document();
                doc.add(new StringField("id", entry.getId(), Field.Store.YES));
                doc.add(new TextField("title", entry.getTitle(), Field.Store.YES));
                doc.add(new TextField("content", entry.getContent(), Field.Store.YES));
                if (entry.getMetadata() != null) {
                    entry.getMetadata().forEach((k, v) ->
                            doc.add(new StringField("meta_" + k, v, Field.Store.YES)));
                }
                indexWriter.updateDocument(new Term("id", entry.getId()), doc);
            }
            indexWriter.commit();
            log.info("Batch indexed {} documents", entries.size());
        } catch (IOException e) {
            log.error("Batch indexing failed", e);
        }
    }

    /**
     * 删除文档
     */
    public void delete(String id) {
        try {
            indexWriter.deleteDocuments(new Term("id", id));
            indexWriter.commit();
        } catch (IOException e) {
            log.error("Failed to delete document: {}", id, e);
        }
    }

    /**
     * 获取索引文档数
     */
    public long count() {
        try (IndexReader reader = DirectoryReader.open(directory)) {
            return reader.numDocs();
        } catch (IOException e) {
            return 0;
        }
    }

    // ===================== 检索 =====================

    /**
     * 多字段搜索
     */
    public List<SearchResult> search(String query, int maxResults) {
        try (IndexReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);

            // 多字段匹配: title^3 + content
            Map<String, Float> boosts = Map.of("title", 3.0f, "content", 1.0f);
            MultiFieldQueryParser parser = new MultiFieldQueryParser(
                    new String[]{"title", "content"}, analyzer, boosts);
            parser.setDefaultOperator(QueryParser.Operator.OR);

            Query luceneQuery = parser.parse(QueryParser.escape(query));
            TopDocs topDocs = searcher.search(luceneQuery, maxResults);

            // 高亮
            SimpleHTMLFormatter formatter = new SimpleHTMLFormatter("<em>", "</em>");
            QueryScorer scorer = new QueryScorer(luceneQuery);
            Highlighter highlighter = new Highlighter(formatter, scorer);
            Fragmenter fragmenter = new SimpleFragmenter(config.getFragmentSize());
            highlighter.setTextFragmenter(fragmenter);

            List<SearchResult> results = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                String content = doc.get("content");
                String highlight = content;
                try {
                    String best = highlighter.getBestFragment(analyzer, "content", content);
                    if (best != null) highlight = best;
                } catch (Exception ignored) {}

                Map<String, String> meta = new HashMap<>();
                String[] fields = doc.getFields().stream().map(IndexableField::name).toArray(String[]::new);
                for (String f : fields) {
                    if (f.startsWith("meta_")) {
                        meta.put(f.substring(5), doc.get(f));
                    }
                }

                results.add(SearchResult.builder()
                        .id(doc.get("id"))
                        .title(doc.get("title"))
                        .content(content)
                        .highlight(highlight)
                        .score(scoreDoc.score)
                        .metadata(meta)
                        .build());
            }
            return results;
        } catch (Exception e) {
            log.error("Search failed: {}", query, e);
            return List.of();
        }
    }

    /**
     * 前缀搜索(自动补全)
     */
    public List<SearchResult> suggest(String prefix, int limit) {
        try (IndexReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            PrefixQuery prefixQuery = new PrefixQuery(new Term("title", prefix.toLowerCase()));
            TopDocs topDocs = searcher.search(prefixQuery, limit);

            List<SearchResult> results = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                results.add(SearchResult.builder()
                        .id(doc.get("id"))
                        .title(doc.get("title"))
                        .score(scoreDoc.score)
                        .build());
            }
            return results;
        } catch (IOException e) {
            return List.of();
        }
    }

    @Override
    public void close() {
        try {
            if (indexWriter != null) indexWriter.close();
            directory.close();
        } catch (IOException e) {
            log.warn("Error closing Lucene resources", e);
        }
    }

    // =========== 内部类 ===========

    @lombok.Data
    @lombok.Builder
    public static class DocumentEntry {
        private String id;
        private String title;
        private String content;
        private Map<String, String> metadata;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SearchResult {
        private String id;
        private String title;
        private String content;
        private String highlight;
        private double score;
        private Map<String, String> metadata;
    }
}
