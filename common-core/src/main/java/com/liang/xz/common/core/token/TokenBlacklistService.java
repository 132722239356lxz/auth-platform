package com.liang.xz.common.core.token;

import java.time.Duration;

/**
 * <p>Token 黑名单服务 —— 支撑"退出登录后 Token 立即失效"</p>
 *
 * <p><b>背景：</b>JWT 是无状态凭证，签发后在有效期内天然可被继续使用。
 * 若用户退出登录（或被强制下线、修改密码）后服务端不做任何处理，
 * 旧 Token 在过期前依然能通过校验，属于典型的"会话未真正终止"漏洞。
 * 本服务通过在 Redis 中登记已吊销的 Token 标识（{@code jti}）来弥补这一缺陷。</p>
 *
 * <p><b>实现要点：</b></p>
 * <ul>
 *   <li>存储键为 {@code auth:token:blacklist:{jti}}，TTL 设为该 Token 的剩余有效期，
 *       过期后由 Redis 自动清理，避免黑名单无限膨胀；</li>
 *   <li>使用 Redis 而非内存 Map，保证多实例部署与重启后依然生效；</li>
 *   <li>网关（Reactive）与资源服务器（Servlet）各自实现本接口，
 *       双方必须遵循相同的 Key 规范，见 {@link #BLACKLIST_KEY_PREFIX}。</li>
 * </ul>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public interface TokenBlacklistService {

    /**
     * 黑名单 Key 前缀 —— 网关与各业务服务必须使用同一前缀，否则吊销无法跨服务生效。
     */
    String BLACKLIST_KEY_PREFIX = "auth:token:blacklist:";

    /**
     * 判断指定 jti 是否已被吊销。
     *
     * @param jti JWT 的唯一标识（{@code jti} claim）
     * @return 已被吊销返回 true
     */
    boolean isRevoked(String jti);

    /**
     * 吊销指定 Token：将其 jti 写入黑名单。
     *
     * @param jti         JWT 的唯一标识
     * @param remainingTtl 该 Token 的剩余有效期，黑名单将按此设置 TTL（到期自动清除）
     */
    void revoke(String jti, Duration remainingTtl);

    /**
     * 构造黑名单 Key（统一规范，供各实现复用）。
     *
     * @param jti JWT 唯一标识
     * @return 完整的 Redis Key
     */
    default String buildKey(String jti) {
        return BLACKLIST_KEY_PREFIX + jti;
    }
}
