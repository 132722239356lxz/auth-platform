package com.liang.xz.server.service;

import com.liang.xz.common.core.redis.RedisHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * <p>短信验证码服务 —— 生成、存储、校验</p>
 *
 * <p>存储策略:</p>
 * <ul>
 *   <li>验证码 key: {@code sms:code:{phone}}，TTL 5 分钟</li>
 *   <li>发送频率限制 key: {@code sms:rate:{phone}}，TTL 60 秒</li>
 *   <li>单手机号每日上限 key: {@code sms:daily:{phone}}，TTL 到当天结束</li>
 * </ul>
 *
 * <p>校验通过后立即清除验证码，一次有效。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsCodeService {

    private static final String CODE_PREFIX = "sms:code:";
    private static final String RATE_PREFIX = "sms:rate:";
    private static final String DAILY_PREFIX = "sms:daily:";

    /** 验证码有效期（分钟） */
    private static final long CODE_TTL_MINUTES = 5;

    /** 发送间隔（秒） */
    private static final long RATE_LIMIT_SECONDS = 60;

    /** 单手机号每日最大发送次数 */
    private static final int DAILY_MAX_SEND = 10;

    /** 验证码长度 */
    private static final int CODE_LENGTH = 6;

    private final RedisHelper redisHelper;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 发送短信验证码。
     *
     * <p>流程: 检查发送频率 → 检查每日上限 → 生成6位随机码 → 存入 Redis → 日志输出（接入真实短信通道后替换）</p>
     *
     * @param phone 手机号
     * @throws IllegalArgumentException 发送过于频繁或超过每日上限
     */
    public void sendCode(String phone) {
        // 1. 频率限制：60 秒内不可重复发送
        String rateKey = RATE_PREFIX + phone;
        if (Boolean.TRUE.equals(redisHelper.hasKey(rateKey))) {
            Long remaining = redisHelper.getExpire(rateKey);
            throw new IllegalArgumentException("短信发送过于频繁，请" + (remaining != null ? remaining : 60) + "秒后重试");
        }

        // 2. 单日上限
        String dailyKey = DAILY_PREFIX + phone;
        String dailyCount = redisHelper.getString(dailyKey);
        if (dailyCount != null && Integer.parseInt(dailyCount) >= DAILY_MAX_SEND) {
            throw new IllegalArgumentException("今日短信发送次数已达上限(" + DAILY_MAX_SEND + "次)，请明天再试");
        }

        // 3. 生成 6 位随机验证码
        String code = generateCode();

        // 4. 存入 Redis（5 分钟有效）
        String codeKey = CODE_PREFIX + phone;
        redisHelper.setString(codeKey, code, CODE_TTL_MINUTES, TimeUnit.MINUTES);
        log.info("[SmsCode] 验证码已生成: phone={}, code={}, expire={}min", phone, code, CODE_TTL_MINUTES);

        // 5. 设置频率限制
        redisHelper.setString(rateKey, "1", RATE_LIMIT_SECONDS, TimeUnit.SECONDS);

        // 6. 每日计数器 +1，过期时间到当天 23:59:59
        long secondsUntilMidnight = getSecondsUntilMidnight();
        Long newCount = redisHelper.getStringRedisTemplate().opsForValue().increment(dailyKey);
        redisHelper.expire(dailyKey, secondsUntilMidnight, TimeUnit.SECONDS);
        log.debug("[SmsCode] 当日发送计数: phone={}, count={}", phone, newCount);
    }

    /**
     * 校验短信验证码。
     *
     * <p>校验成功后立即删除验证码，防止重复使用。</p>
     *
     * @param phone 手机号
     * @param code  用户输入的验证码
     * @return true 验证通过，false 验证码错误或已过期
     */
    public boolean verifyCode(String phone, String code) {
        if (phone == null || phone.isBlank() || code == null || code.isBlank()) {
            return false;
        }
        String codeKey = CODE_PREFIX + phone;
        String storedCode = redisHelper.getString(codeKey);
        if (storedCode == null || storedCode.isEmpty()) {
            log.warn("[SmsCode] 验证码不存在或已过期: phone={}", phone);
            return false;
        }
        if (!storedCode.equals(code.trim())) {
            log.warn("[SmsCode] 验证码错误: phone={}, expected={}, actual={}", phone, storedCode, code);
            return false;
        }
        // 验证成功，删除验证码（一次性使用）
        redisHelper.delete(codeKey);
        log.info("[SmsCode] 验证码校验成功: phone={}", phone);
        return true;
    }

    /**
     * 生成 6 位随机数字验证码。
     */
    private String generateCode() {
        int code = secureRandom.nextInt((int) Math.pow(10, CODE_LENGTH));
        return String.format("%0" + CODE_LENGTH + "d", code);
    }

    /**
     * 计算距离当天 23:59:59 的剩余秒数。
     */
    private long getSecondsUntilMidnight() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return java.time.Duration.between(now, midnight).getSeconds();
    }
}
