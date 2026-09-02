package com.liang.xz.common.core.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * <p>JSON 序列化/反序列化工具类</p>
 *
 * <p>基于 Jackson，统一配置:</p>
 * <ul>
 *   <li>Java 8 时间类型支持 (LocalDate/LocalDateTime/LocalTime)</li>
 *   <li>忽略未知字段，不抛异常</li>
 *   <li>日期格式 yyyy-MM-dd HH:mm:ss</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = buildMapper();

    private JsonUtil() {
        // 工具类禁止实例化
    }

    private static ObjectMapper buildMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // ---- 时间格式 ----
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        javaTimeModule.addDeserializer(LocalDateTime.class,
                new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        javaTimeModule.addSerializer(LocalDate.class,
                new LocalDateSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        javaTimeModule.addDeserializer(LocalDate.class,
                new LocalDateDeserializer(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        javaTimeModule.addSerializer(LocalTime.class,
                new LocalTimeSerializer(DateTimeFormatter.ofPattern("HH:mm:ss")));
        javaTimeModule.addDeserializer(LocalTime.class,
                new LocalTimeDeserializer(DateTimeFormatter.ofPattern("HH:mm:ss")));
        mapper.registerModule(javaTimeModule);

        // ---- 宽容模式 ----
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        return mapper;
    }

    /**
     * 获取原始 ObjectMapper（供需要自定义配置的场景使用）
     */
    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    // ==================== 序列化 ====================

    /** 对象 → JSON 字符串 */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 序列化失败: " + e.getMessage(), e);
        }
    }

    /** 对象 → 格式化 JSON 字符串 */
    public static String toPrettyJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 对象 → JSON（静默失败，异常时返回默认值）
     *
     * @param obj          要序列化的对象
     * @param defaultValue 序列化失败时的默认值
     */
    public static String toJsonOrDefault(Object obj, String defaultValue) {
        try {
            return toJson(obj);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // ==================== 反序列化 ====================

    /** JSON → 对象 */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 反序列化失败: " + e.getMessage(), e);
        }
    }

    /** JSON → List<T> */
    public static <T> List<T> fromJsonList(String json, Class<T> elementClass) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JavaType javaType = MAPPER.getTypeFactory()
                    .constructCollectionType(List.class, elementClass);
            return MAPPER.readValue(json, javaType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 反序列化失败: " + e.getMessage(), e);
        }
    }

    /** JSON → Map<String, Object> */
    public static Map<String, Object> fromJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 反序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * JSON → 对象（静默失败，异常时返回默认值）
     */
    public static <T> T fromJsonOrDefault(String json, Class<T> clazz, T defaultValue) {
        try {
            T result = fromJson(json, clazz);
            return result != null ? result : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // ==================== 转换 ====================

    /**
     * 对象类型转换（通过 JSON 中转，支持属性名不一致时的映射）
     * 性能敏感场景优先使用 BeanUtils.copyProperties
     */
    public static <T> T convert(Object source, Class<T> targetClass) {
        if (source == null) {
            return null;
        }
        return fromJson(toJson(source), targetClass);
    }
}
