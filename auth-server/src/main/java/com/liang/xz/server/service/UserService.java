package com.liang.xz.server.service;

import com.liang.xz.server.dto.UserRegisterRequest;
import com.liang.xz.server.dto.UserResponse;
import com.liang.xz.server.entity.UserEntity;
import com.liang.xz.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <p>用户管理服务 —— 用户注册、查询、启用/禁用</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ======================== 注册 ========================

    /**
     * 注册新用户(自助注册或管理员创建)
     */
    @Transactional
    public UserResponse register(UserRegisterRequest request) {
        // 手机号唯一性校验（主要登录凭证）
        if (userRepository.findByPhoneIncludeDisabled(request.getPhone()).isPresent()) {
            throw new IllegalArgumentException("手机号 [" + request.getPhone() + "] 已被注册");
        }

        // 用户名唯一性校验（兼容）
        String username = request.getUsername() != null ? request.getUsername() : request.getPhone();
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("用户名 [" + username + "] 已存在");
        }

        UserEntity user = UserEntity.builder()
                .username(username)
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname() != null ? request.getNickname() : username)
                .email(request.getEmail())
                .phone(request.getPhone())
                .userType(request.getUserType() != null ? request.getUserType() : "user")
                .tenantId(request.getTenantId() != null ? request.getTenantId() : "default")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();

        userRepository.insert(user);
        log.info("[UserService] 用户注册成功: phone={}, username={}", user.getPhone(), user.getUsername());
        return toResponse(user);
    }

    // ======================== 查询 ========================

    /** 按用户名查询 */
    public Optional<UserResponse> findByUsername(String username) {
        return userRepository.findByUsername(username).map(this::toResponse);
    }

    /** 查询所有用户 */
    public List<UserResponse> listAll() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ======================== 管理 ========================

    /** 启用用户 */
    @Transactional
    public boolean enable(Long id) {
        return userRepository.updateEnabled(id, true) > 0;
    }

    /** 禁用用户 */
    @Transactional
    public boolean disable(Long id) {
        return userRepository.updateEnabled(id, false) > 0;
    }

    /** 删除用户 */
    @Transactional
    public boolean delete(Long id) {
        return userRepository.deleteById(id) > 0;
    }

    // ======================== 辅助 ========================

    private UserResponse toResponse(UserEntity entity) {
        return UserResponse.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .nickname(entity.getNickname())
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .userType(entity.getUserType())
                .tenantId(entity.getTenantId())
                .enabled(entity.getEnabled())
                .createTime(entity.getCreateTime())
                .lastLoginTime(entity.getLastLoginTime())
                .build();
    }
}
