package com.liang.xz.server.service;

import com.liang.xz.server.entity.UserEntity;
import com.liang.xz.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>Spring Security 用户详情加载服务 —— 支持手机号或用户名登录</p>
 *
 * <p>认证流程:</p>
 * <ol>
 *   <li>用户提交登录表单(phone 或 username + password)</li>
 *   <li>Spring Security 调用 loadUserByUsername(loginIdentifier) 加载用户</li>
 *   <li>优先按手机号匹配，未命中则按用户名匹配</li>
 *   <li>无论匹配方式如何，最终返回的 principal 统一使用 username</li>
 *   <li>BCryptPasswordEncoder 自动匹配密码</li>
 *   <li>认证成功后跳转到 OAuth2 授权确认页</li>
 * </ol>
 *
 * <p>兼容策略: 先按手机号查询，查不到则退回到用户名查询（兼容旧数据），
 * 但 principal name 始终使用 username，确保 SecurityContext 中身份标识一致。</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String loginIdentifier) throws UsernameNotFoundException {
        // 优先按手机号查询
        UserEntity user = userRepository.findByPhone(loginIdentifier).orElse(null);

        if (user == null) {
            // 兼容旧数据：退回到用户名查询
            user = userRepository.findByUsername(loginIdentifier).orElse(null);
        }

        if (user == null) {
            log.warn("[UserDetails] 用户不存在: identifier={}", loginIdentifier);
            throw new UsernameNotFoundException("手机号或用户 [" + loginIdentifier + "] 不存在");
        }

        if (user.getEnabled() == null || !user.getEnabled()) {
            log.warn("[UserDetails] 账号已禁用: identifier={}", loginIdentifier);
            throw new UsernameNotFoundException("账号 [" + loginIdentifier + "] 已被禁用");
        }

        // 构建权限列表
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getUserType().toUpperCase())
        );

        log.info("[UserDetails] 用户认证成功: username={}, phone={}, userType={}",
                user.getUsername(), user.getPhone(), user.getUserType());

        return new User(
                user.getUsername(),
                user.getPassword(),
                user.getEnabled(),
                user.getAccountNonExpired(),
                user.getCredentialsNonExpired(),
                user.getAccountNonLocked(),
                authorities
        );
    }
}
