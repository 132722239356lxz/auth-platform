package com.liang.xz.common.core.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>缓存映射服务 —— 解决实体关联属性多表查询的性能问题</p>
 *
 * <p>典型业务场景:</p>
 * <pre>{@code
 * // 场景1: Order.userId -> User 映射（减少 JOIN 查询）
 * List<Order> orders = orderMapper.selectList(...);
 * Set<Long> userIds = orders.stream().map(Order::getUserId).collect(Collectors.toSet());
 * Map<Long, User> userMap = cacheMappingService.batchLoadByIds(
 *     "cache:user", userIds,
 *     ids -> userMapper.selectBatchIds(ids),  // DB 兜底
 *     User.class, Duration.ofMinutes(30)
 * );
 * orders.forEach(o -> o.setUserName(userMap.get(o.getUserId()).getNickname()));
 *
 * // 场景2: 一对多映射，先查关联ID列表再查详情
 * Map<Long, List<OrderItem>> itemMap = cacheMappingService.batchLoadMapping(
 *     "cache:order:items", orderIds,
 *     ids -> orderItemMapper.selectByOrderIds(ids),  // 返回 Map<Long, List<OrderItem>>
 *     Duration.ofMinutes(10)
 * );
 * }</pre>
 *
 * <p>核心优化:</p>
 * <ul>
 *   <li>批量查缓存，未命中部分合并为一次 DB 查询（减少 DB 往返）</li>
 *   <li>缓存穿透保护：缓存空值标记</li>
 *   <li>pipeline 批量写入回填缓存</li>
 *   <li>key 前缀隔离，支持按前缀批量清除</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Component
public class CacheMappingService {

    private static final Logger log = LoggerFactory.getLogger(CacheMappingService.class);

    /** 空值占位符（防止缓存穿透） */
    private static final String NULL_PLACEHOLDER = "__NULL__";

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public CacheMappingService(RedisTemplate<String, Object> redisTemplate,
                               StringRedisTemplate stringRedisTemplate) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ==================== 批量加载：主键 → 实体对象 ====================

    /**
     * <p>批量加载实体（先查缓存，未命中走 DB 并回填）</p>
     *
     * <p>核心流程:</p>
     * <ol>
     *   <li>批量 mget 查询所有缓存 key</li>
     *   <li>区分命中 / 未命中 / 缓存空值</li>
     *   <li>未命中的 ID 合并为一次 DB 查询</li>
     *   <li>批量 mset 回填缓存（pipeline）</li>
     * </ol>
     *
     * @param keyPrefix   缓存 key 前缀（如 "cache:user"）
     * @param ids         主键集合
     * @param dbLoader    DB 兜底查询（入参：未命中的ID集合，出参：ID→实体映射）
     * @param entityClass 实体类型（用于反序列化）
     * @param ttl         缓存过期时间
     * @param <ID>        主键类型
     * @param <T>         实体类型
     * @return ID → 实体的映射 Map
     */
    public <ID, T> Map<ID, T> batchLoadByIds(String keyPrefix, Set<ID> ids,
                                              Function<Set<ID>, Map<ID, T>> dbLoader,
                                              Class<T> entityClass, Duration ttl) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }

        // 1. 构造缓存 key 列表
        List<String> cacheKeys = ids.stream()
                .map(id -> buildCacheKey(keyPrefix, id))
                .collect(Collectors.toList());

        // 2. 批量查缓存
        List<Object> cachedValues = redisTemplate.opsForValue().multiGet(cacheKeys);
        if (cachedValues == null) {
            cachedValues = Collections.emptyList();
        }

        // 3. 区分命中 / 未命中
        Map<ID, T> result = new HashMap<>();
        Set<ID> missIds = new HashSet<>();
        List<ID> idList = new ArrayList<>(ids);

        for (int i = 0; i < idList.size(); i++) {
            ID id = idList.get(i);
            Object cached = i < cachedValues.size() ? cachedValues.get(i) : null;

            if (cached == null) {
                // 缓存未命中
                missIds.add(id);
            } else if (NULL_PLACEHOLDER.equals(cached)) {
                // 命中空值标记（防止穿透）
                // 不加入 result，表示该 ID 在 DB 中也不存在
            } else if (entityClass.isInstance(cached)) {
                result.put(id, entityClass.cast(cached));
            }
        }

        // 4. 未命中的走 DB
        if (!missIds.isEmpty()) {
            log.debug("Cache miss for prefix [{}], ids: {}", keyPrefix, missIds);
            Map<ID, T> dbResult = dbLoader.apply(missIds);

            // 回填缓存
            for (ID id : missIds) {
                String key = buildCacheKey(keyPrefix, id);
                T entity = dbResult != null ? dbResult.get(id) : null;
                if (entity != null) {
                    redisTemplate.opsForValue().set(key, entity, ttl);
                    result.put(id, entity);
                } else {
                    // 缓存空值（短过期，防止穿透）
                    redisTemplate.opsForValue().set(key, NULL_PLACEHOLDER, Duration.ofMinutes(1));
                }
            }
        }

        return result;
    }

    // ==================== 批量加载映射：主键 → 关联列表 ====================

    /**
     * <p>批量加载一对多映射（主键 → 关联实体列表）</p>
     *
     * <p>适用场景: 根据多个 orderId 批量查询对应的 OrderItem 列表</p>
     *
     * @param keyPrefix  缓存 key 前缀
     * @param ids        主键集合
     * @param dbLoader   DB 兜底（入参：未命中ID集合，出参：ID→列表映射）
     * @param ttl        缓存过期时间
     * @param <ID>       主键类型
     * @param <T>        关联实体类型
     * @return ID → 关联实体列表的映射
     */
    @SuppressWarnings("unchecked")
    public <ID, T> Map<ID, List<T>> batchLoadMapping(String keyPrefix, Set<ID> ids,
                                                      Function<Set<ID>, Map<ID, List<T>>> dbLoader,
                                                      Duration ttl) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> cacheKeys = ids.stream()
                .map(id -> buildCacheKey(keyPrefix, id))
                .collect(Collectors.toList());

        List<Object> cachedValues = redisTemplate.opsForValue().multiGet(cacheKeys);
        if (cachedValues == null) {
            cachedValues = Collections.emptyList();
        }

        Map<ID, List<T>> result = new HashMap<>();
        Set<ID> missIds = new HashSet<>();
        List<ID> idList = new ArrayList<>(ids);

        for (int i = 0; i < idList.size(); i++) {
            ID id = idList.get(i);
            Object cached = i < cachedValues.size() ? cachedValues.get(i) : null;

            if (cached == null) {
                missIds.add(id);
            } else if (NULL_PLACEHOLDER.equals(cached)) {
                result.put(id, Collections.emptyList());
            } else if (cached instanceof List) {
                result.put(id, (List<T>) cached);
            }
        }

        if (!missIds.isEmpty()) {
            Map<ID, List<T>> dbResult = dbLoader.apply(missIds);
            for (ID id : missIds) {
                String key = buildCacheKey(keyPrefix, id);
                List<T> entities = dbResult != null ? dbResult.get(id) : null;
                if (entities != null) {
                    redisTemplate.opsForValue().set(key, entities, ttl);
                    result.put(id, entities);
                } else {
                    redisTemplate.opsForValue().set(key, Collections.emptyList(), ttl);
                    result.put(id, Collections.emptyList());
                }
            }
        }

        return result;
    }

    // ==================== 单对象加载（常用快捷方法） ====================

    /**
     * 按 ID 加载单个实体（缓存穿透保护）
     *
     * @param keyPrefix   缓存 key 前缀
     * @param id          主键
     * @param dbLoader    DB 兜底查询
     * @param entityClass 实体类型
     * @param ttl         缓存过期时间
     */
    public <ID, T> T loadById(String keyPrefix, ID id, Function<ID, T> dbLoader,
                               Class<T> entityClass, Duration ttl) {
        String key = buildCacheKey(keyPrefix, id);
        Object cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            if (NULL_PLACEHOLDER.equals(cached)) {
                return null;
            }
            if (entityClass.isInstance(cached)) {
                return entityClass.cast(cached);
            }
        }

        T entity = dbLoader.apply(id);
        if (entity != null) {
            redisTemplate.opsForValue().set(key, entity, ttl);
        } else {
            redisTemplate.opsForValue().set(key, NULL_PLACEHOLDER, Duration.ofMinutes(1));
        }
        return entity;
    }

    // ==================== 缓存失效 ====================

    /**
     * 按 ID 清除单个缓存
     */
    public void evictById(String keyPrefix, Object id) {
        redisTemplate.delete(buildCacheKey(keyPrefix, id));
    }

    /**
     * 按 ID 集合批量清除缓存
     */
    public void evictByIds(String keyPrefix, Collection<?> ids) {
        List<String> keys = ids.stream()
                .map(id -> buildCacheKey(keyPrefix, id))
                .collect(Collectors.toList());
        redisTemplate.delete(keys);
    }

    /**
     * 按前缀清除所有缓存（谨慎使用）
     */
    public void evictByPrefix(String keyPrefix) {
        Set<String> keys = redisTemplate.keys(keyPrefix + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.debug("Evicted {} cache keys with prefix [{}]", keys.size(), keyPrefix);
        }
    }

    // ==================== 内部工具 ====================

    private String buildCacheKey(String prefix, Object id) {
        return prefix + ":" + id;
    }
}
