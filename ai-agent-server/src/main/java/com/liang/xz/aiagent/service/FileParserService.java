package com.liang.xz.aiagent.service;

import com.liang.xz.aiagent.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>文件解析服务 — 使用Apache Tika解析多种格式文件为纯文本</p>
 *
 * <p>支持格式: PDF, DOCX, XLSX, TXT, MD, HTML, CSV, JSON</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class FileParserService {

    private final Tika tika;
    private final Set<String> supportedFormats;

    public FileParserService(AiProperties aiProperties) {
        this.tika = new Tika();
        this.supportedFormats = aiProperties.getFileUpload().getSupportedFormats().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    /**
     * 文件解析结果
     */
    @lombok.Builder
    @lombok.Data
    public static class ParseResult {
        private String text;
        private String contentType;
        private String mediaType;
        private long parseTimeMs;
        private int textLength;
        private String error;
    }

    /**
     * 解析文件字节数组为纯文本
     *
     * @param fileData 文件字节数据
     * @param fileName 原始文件名(用于推断格式)
     * @return 解析结果
     */
    public ParseResult parse(byte[] fileData, String fileName) {
        long start = System.currentTimeMillis();
        String extension = getExtension(fileName);

        // 校验格式
        if (!supportedFormats.contains(extension)) {
            return ParseResult.builder()
                    .error("不支持的文件格式: ." + extension + "，支持: " + supportedFormats)
                    .parseTimeMs(System.currentTimeMillis() - start)
                    .build();
        }

        try (InputStream inputStream = new ByteArrayInputStream(fileData)) {
            // 自动检测媒体类型
            String mediaType = tika.detect(inputStream, fileName);
            log.info("[FileParser] 检测到文件类型: {} -> {}", fileName, mediaType);

            // 使用AutoDetectParser解析
            BodyContentHandler handler = new BodyContentHandler(-1); // -1 = 无大小限制
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);

            ParseContext context = new ParseContext();
            Parser parser = new AutoDetectParser();

            // 重新创建流(因为detect已经消耗了之前的流)
            try (InputStream parseStream = new ByteArrayInputStream(fileData)) {
                parser.parse(parseStream, handler, metadata, context);
            }

            String text = handler.toString().trim();
            long elapsed = System.currentTimeMillis() - start;

            log.info("[FileParser] 解析完成: {} -> {} chars, {}ms", fileName, text.length(), elapsed);

            if (text.isEmpty() && !isMediaExtension(extension)) {
                return ParseResult.builder()
                        .error("文件内容为空或无法提取文本")
                        .contentType(extension.toUpperCase())
                        .mediaType(mediaType)
                        .parseTimeMs(elapsed)
                        .build();
            }

            return ParseResult.builder()
                    .text(text)
                    .contentType(extension.toUpperCase())
                    .mediaType(mediaType)
                    .parseTimeMs(elapsed)
                    .textLength(text.length())
                    .build();

        } catch (IOException | TikaException | SAXException e) {
            log.error("[FileParser] 文件解析失败: {}", fileName, e);
            return ParseResult.builder()
                    .error("文件解析异常: " + e.getMessage())
                    .parseTimeMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    /**
     * 检查文件格式是否支持
     */
    public boolean isSupported(String fileName) {
        return supportedFormats.contains(getExtension(fileName));
    }

    /**
     * 获取支持的文件格式列表
     */
    public Set<String> getSupportedFormats() {
        return supportedFormats;
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "txt";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    private boolean isMediaExtension(String extension) {
        return Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg",
                "mp3", "wav", "flac", "aac", "ogg",
                "mp4", "avi", "mkv", "mov", "wmv").contains(extension);
    }
}
