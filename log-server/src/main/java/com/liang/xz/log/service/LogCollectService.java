package com.liang.xz.log.service;

import com.liang.xz.common.core.async.AsyncHelper;
import com.liang.xz.log.dto.LogReportRequest;
import com.liang.xz.log.dto.LogReportRequest.LogEntry;
import com.liang.xz.log.entity.LogRecord;
import com.liang.xz.log.enums.LogCategory;
import com.liang.xz.log.enums.ModuleType;
import com.liang.xz.log.repository.LogRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 日志收集服务
 * <p>
 * 接收各模块上报的日志, 进行标准化处理后持久化存储
 *
 * @author liang
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogCollectService {

    private final LogRecordRepository logRecordRepository;

    /**
     * 批量收集日志 (异步持久化)
     */
    public void collectBatch(LogReportRequest request) {
        if (request == null || request.getEntries() == null || request.getEntries().isEmpty()) {
            return;
        }
        AsyncHelper.runAsync(() -> {
            List<LogRecord> records = new ArrayList<>();
            for (LogEntry entry : request.getEntries()) {
                records.add(toEntity(entry));
            }
            logRecordRepository.saveAll(records);
            log.debug("[log-server] 批量收集 {} 条日志", records.size());
        });
    }

    /**
     * 单条收集 (异步持久化)
     */
    public void collectSingle(LogEntry entry) {
        if (entry == null) return;
        AsyncHelper.runAsync(() -> {
            logRecordRepository.save(toEntity(entry));
        });
    }

    /**
     * 单条收集 (同步, 用于必须确保落盘的场景)
     */
    public void collectSingleSync(LogEntry entry) {
        if (entry == null) return;
        logRecordRepository.save(toEntity(entry));
    }

    // ======================== 内部转换 ========================

    private LogRecord toEntity(LogEntry entry) {
        String errorFingerprint = null;
        if ("ERROR".equalsIgnoreCase(entry.getLevel())
                && entry.getExceptionType() != null) {
            errorFingerprint = computeErrorFingerprint(
                    entry.getExceptionType(),
                    Optional.ofNullable(entry.getExceptionStack())
                            .map(s -> extractRootLine(s))
                            .orElse(entry.getMessage())
            );
        }

        return LogRecord.builder()
                .traceId(entry.getTraceId())
                .module(normalizeModule(entry.getModule()))
                .category(normalizeCategory(entry.getCategory(), entry.getLevel(), entry.getExceptionType()))
                .level(entry.getLevel())
                .className(entry.getClassName())
                .methodName(entry.getMethodName())
                .message(truncate(entry.getMessage(), 500))
                .fullMessage(entry.getFullMessage())
                .exceptionStack(entry.getExceptionStack())
                .exceptionType(entry.getExceptionType())
                .errorFingerprint(errorFingerprint)
                .username(entry.getUsername())
                .clientIp(entry.getClientIp())
                .requestUri(entry.getRequestUri())
                .httpMethod(entry.getHttpMethod())
                .httpStatus(entry.getHttpStatus())
                .costTime(entry.getCostTime())
                .metadata(entry.getMetadata())
                .logTime(Optional.ofNullable(entry.getLogTime()).orElse(LocalDateTime.now()))
                .createTime(LocalDateTime.now())
                .build();
    }

    /**
     * 计算错误指纹
     * 规则: SHA256( 异常类型 + "|" + 根因行 ), 忽略变量参数, 保证同类错误指纹一致
     */
    public static String computeErrorFingerprint(String exceptionType, String keyLine) {
        String raw = (exceptionType != null ? exceptionType : "") + "|" + (keyLine != null ? normalizeKeyLine(keyLine) : "");
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 提取堆栈的根因行(最后一条 Caused by 或第一条 at) */
    private String extractRootLine(String stack) {
        if (stack == null || stack.isEmpty()) return "";
        // 找最后的 Caused by 行
        String[] lines = stack.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].trim().startsWith("Caused by:")) {
                return lines[i].trim();
            }
        }
        // 找第一条 at 行
        for (String line : lines) {
            if (line.trim().startsWith("at ")) {
                return line.trim();
            }
        }
        return lines[0].trim();
    }

    /** 标准化关键行, 移除变量参数(如数字ID、IP等) */
    private static String normalizeKeyLine(String line) {
        if (line == null) return "";
        return line
                .replaceAll("\\b\\d{10,}\\b", "{TIMESTAMP}")   // 时间戳
                .replaceAll("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b", "{IP}")  // IP
                .replaceAll("\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b", "{UUID}")
                .replaceAll("\"[^\"]{50,}\"", "\"{LONG_STRING}\"")  // 超长字符串
                .replaceAll("\\b\\d{5,}\\b", "{NUM}");        // 长数字
    }

    private String normalizeModule(String module) {
        if (module == null || module.isEmpty()) return ModuleType.UNKNOWN.getCode();
        try {
            ModuleType.fromCode(module);
            return module;
        } catch (Exception e) {
            return ModuleType.UNKNOWN.getCode();
        }
    }

    private String normalizeCategory(String category, String level, String exceptionType) {
        if (category != null && !category.isEmpty()) {
            try {
                LogCategory.fromCode(category);
                return category;
            } catch (Exception ignored) {}
        }
        // 自动推断
        if ("ERROR".equalsIgnoreCase(level) && exceptionType != null) {
            return LogCategory.EXCEPTION.getCode();
        }
        return LogCategory.APPLICATION.getCode();
    }

    private String truncate(String msg, int maxLen) {
        if (msg == null) return null;
        return msg.length() <= maxLen ? msg : msg.substring(0, maxLen) + "...";
    }
}
