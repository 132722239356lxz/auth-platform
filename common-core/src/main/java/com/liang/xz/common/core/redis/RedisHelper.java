package com.liang.xz.common.core.redis;

import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * <p>Redis 通用工具类</p>
 *
 * <p>提供以下能力:</p>
 * <ul>
 *   <li>String / Hash / List / Set / ZSet 常用操作</li>
 *   <li>通用过期时间设置</li>
 *   <li>分布式锁（基于 SET NX）</li>
 *   <li>计数器 / 限流辅助</li>
 *   <li>批量操作</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Component
public class RedisHelper {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public RedisHelper(RedisTemplate<String, Object> redisTemplate,
                       StringRedisTemplate stringRedisTemplate) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ==================== Key 操作 ====================

    /** 设置过期时间 */
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    /** 获取过期时间(秒) */
    public Long getExpire(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /** 判断 key 是否存在 */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /** 删除 key */
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    /** 批量删除 */
    public Long delete(Collection<String> keys) {
        return redisTemplate.delete(keys);
    }

    /** 匹配删除（支持通配符 *） */
    public Long deleteByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            return redisTemplate.delete(keys);
        }
        return 0L;
    }

    // ==================== String 操作 ====================

    /** 设置值 */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /** 设置值并指定过期时间 */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /** 获取值 */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) redisTemplate.opsForValue().get(key);
    }

    /** 获取值，不存在时返回默认值 */
    public <T> T getOrDefault(String key, T defaultValue) {
        T value = get(key);
        return value != null ? value : defaultValue;
    }

    /** 获取字符串值 */
    public String getString(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /** 设置字符串值 */
    public void setString(String key, String value, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /** 递增 */
    public Long incr(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    /** 递减 */
    public Long decr(String key) {
        return redisTemplate.opsForValue().decrement(key);
    }

    /** 设置 NX（不存在才设置） */
    public Boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
    }

    // ==================== Hash 操作 ====================

    public void hSet(String key, String field, Object value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T hGet(String key, String field) {
        return (T) redisTemplate.opsForHash().get(key, field);
    }

    public Map<Object, Object> hGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    public Long hDelete(String key, Object... fields) {
        return redisTemplate.opsForHash().delete(key, fields);
    }

    public Boolean hHasKey(String key, String field) {
        return redisTemplate.opsForHash().hasKey(key, field);
    }

    // ==================== List 操作 ====================

    public Long lPush(String key, Object value) {
        return redisTemplate.opsForList().leftPush(key, value);
    }

    public Long rPush(String key, Object value) {
        return redisTemplate.opsForList().rightPush(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T lPop(String key) {
        return (T) redisTemplate.opsForList().leftPop(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T rPop(String key) {
        return (T) redisTemplate.opsForList().rightPop(key);
    }

    public List<Object> lRange(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    public Long lSize(String key) {
        return redisTemplate.opsForList().size(key);
    }

    // ==================== Set 操作 ====================

    public Long sAdd(String key, Object... values) {
        return redisTemplate.opsForSet().add(key, values);
    }

    public Set<Object> sMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    public Boolean sIsMember(String key, Object value) {
        return redisTemplate.opsForSet().isMember(key, value);
    }

    public Long sSize(String key) {
        return redisTemplate.opsForSet().size(key);
    }

    // ==================== ZSet 操作 ====================

    public Boolean zAdd(String key, Object value, double score) {
        return redisTemplate.opsForZSet().add(key, value, score);
    }

    public Set<Object> zRange(String key, long start, long end) {
        return redisTemplate.opsForZSet().range(key, start, end);
    }

    public Long zSize(String key) {
        return redisTemplate.opsForZSet().size(key);
    }

    // ==================== 分布式锁 ====================

    /**
     * 尝试获取分布式锁（非阻塞）
     *
     * @param lockKey   锁的 key
     * @param requestId 请求标识（建议用 traceId + threadId，释放时校验）
     * @param timeout   锁超时时间
     * @param unit      时间单位
     * @return 是否获取成功
     */
    public Boolean tryLock(String lockKey, String requestId, long timeout, TimeUnit unit) {
        return redisTemplate.opsForValue().setIfAbsent(lockKey, requestId, timeout, unit);
    }

    /**
     * 释放分布式锁（Lua 脚本保证原子性）
     */
    public Boolean releaseLock(String lockKey, String requestId) {
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        Long result = redisTemplate.execute(
                (RedisCallback<Long>) connection -> {
                    Object nativeConnection = connection.getNativeConnection();
                    // 兼容不同连接方式
                    if (nativeConnection instanceof org.springframework.data.redis.connection.ReturnType) {
                        return 0L;
                    }
                    return connection.eval(script.getBytes(),
                            org.springframework.data.redis.connection.ReturnType.INTEGER,
                            1, lockKey.getBytes(), requestId.getBytes());
                });
        return result != null && result == 1L;
    }

    // ==================== 通用缓存 ====================

    /**
     * 缓存查询: 先查缓存，未命中则回调查询源并写入缓存
     *
     * @param key      缓存 key
     * @param timeout  缓存过期时间
     * @param unit     时间单位
     * @param loader   数据加载回调
     * @return 缓存或查询到的数据
     */
    public <T> T cacheGet(String key, long timeout, TimeUnit unit, Supplier<T> loader) {
        T value = get(key);
        if (value != null) {
            return value;
        }
        value = loader.get();
        if (value != null) {
            set(key, value, timeout, unit);
        }
        return value;
    }

    /**
     * 缓存查询(带默认值)
     */
    public <T> T cacheGetOrDefault(String key, long timeout, TimeUnit unit,
                                   Supplier<T> loader, T defaultValue) {
        T value = cacheGet(key, timeout, unit, loader);
        return value != null ? value : defaultValue;
    }

    /**
     * 批量获取对象（底层 multiGet，一次网络往返）
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> multiGet(Collection<String> keys) {
        List<Object> raw = redisTemplate.opsForValue().multiGet(keys);
        if (raw == null) {
            return Collections.emptyList();
        }
        return (List<T>) raw;
    }

    /**
     * 批量获取字符串
     */
    public List<String> multiGetStrings(Collection<String> keys) {
        return stringRedisTemplate.opsForValue().multiGet(keys);
    }

    /**
     * 批量设置
     */
    public void multiSet(Map<String, Object> map) {
        redisTemplate.opsForValue().multiSet(map);
    }

    /**
     * 批量设置(带过期时间，逐个 key 设置 TTL)
     */
    public void multiSet(Map<String, Object> map, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().multiSet(map);
        map.keySet().forEach(key -> expire(key, timeout, unit));
    }

    /**
     * 获取原始 RedisTemplate
     */
    public RedisTemplate<String, Object> getRedisTemplate() {
        return redisTemplate;
    }

    /**
     * 获取 StringRedisTemplate
     */
    public StringRedisTemplate getStringRedisTemplate() {
        return stringRedisTemplate;
    }
}
