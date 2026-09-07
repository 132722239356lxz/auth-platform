package com.liang.xz.system.service;

import com.liang.xz.system.dto.UserRegisterRequest;
import com.liang.xz.system.dto.UserResponse;
import com.liang.xz.system.entity.UserEntity;
import com.liang.xz.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 用户管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse register(UserRegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("用户名 [" + request.getUsername() + "] 已存在");
        }

        UserEntity user = UserEntity.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname() != null ? request.getNickname() : request.getUsername())
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
        log.info("[UserService] 用户注册成功: username={}", user.getUsername());
        return toResponse(user);
    }

    public Optional<UserResponse> findByUsername(String username) {
        return userRepository.findByUsername(username).map(this::toResponse);
    }

    public List<UserResponse> listAll() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public boolean enable(Long id) {
        return userRepository.updateEnabled(id, true) > 0;
    }

    @Transactional
    public boolean disable(Long id) {
        return userRepository.updateEnabled(id, false) > 0;
    }

    @Transactional
    public boolean delete(Long id) {
        return userRepository.deleteById(id) > 0;
    }

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
