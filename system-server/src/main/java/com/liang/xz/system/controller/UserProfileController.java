package com.liang.xz.system.controller;

import com.liang.xz.system.dto.*;
import com.liang.xz.system.entity.UserEntity;
import com.liang.xz.system.repository.UserRepository;
import com.liang.xz.system.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * 用户个人中心控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/user/profile")
@RequiredArgsConstructor
@Tag(name = "个人中心", description = "个人资料管理、修改密码、头像设置")
public class UserProfileController {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    @Operation(summary = "获取个人资料")
    public ApiResponse<UserResponse> getProfile(Principal principal) {
        return userRepository.findByUsername(principal.getName())
                .map(entity -> {
                    UserResponse resp = UserResponse.builder()
                            .id(entity.getId())
                            .username(entity.getUsername())
                            .nickname(entity.getNickname())
                            .email(entity.getEmail())
                            .phone(entity.getPhone())
                            .userType(entity.getUserType())
                            .tenantId(entity.getTenantId())
                            .enabled(entity.getEnabled())
                            .avatar(entity.getAvatar())
                            .createTime(entity.getCreateTime())
                            .lastLoginTime(entity.getLastLoginTime())
                            .lastLoginIp(entity.getLastLoginIp())
                            .build();
                    return ApiResponse.success(resp);
                })
                .orElse(ApiResponse.fail(404, "用户不存在"));
    }

    @PutMapping
    @Operation(summary = "更新个人资料")
    public ApiResponse<Void> updateProfile(
            @Parameter(description = "个人资料") @Valid @RequestBody UpdateProfileRequest request,
            Principal principal) {
        UserEntity user = userRepository.findByUsername(principal.getName())
                .orElse(null);
        if (user == null) {
            return ApiResponse.fail(404, "用户不存在");
        }

        if (request.getNickname() != null) {
            userRepository.updateNickname(user.getId(), request.getNickname());
        }
        if (request.getEmail() != null) {
            userRepository.updateEmail(user.getId(), request.getEmail());
        }
        if (request.getPhone() != null) {
            userRepository.updatePhone(user.getId(), request.getPhone());
        }
        if (request.getAvatar() != null) {
            userRepository.updateAvatar(user.getId(), request.getAvatar());
        }

        log.info("[Profile] 用户资料更新: username={}", principal.getName());
        return ApiResponse.success("资料更新成功", null);
    }

    @PutMapping("/password")
    @Operation(summary = "修改密码")
    public ApiResponse<Void> changePassword(
            @Parameter(description = "密码修改请求") @Valid @RequestBody ChangePasswordRequest request,
            Principal principal) {
        UserEntity user = userRepository.findByUsername(principal.getName())
                .orElse(null);
        if (user == null) {
            return ApiResponse.fail(404, "用户不存在");
        }

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            return ApiResponse.fail(400, "旧密码不正确");
        }

        if (request.getOldPassword().equals(request.getNewPassword())) {
            return ApiResponse.fail(400, "新密码不能与旧密码相同");
        }

        userRepository.updatePassword(user.getId(),
                passwordEncoder.encode(request.getNewPassword()));
        log.info("[Profile] 密码修改成功: username={}", principal.getName());
        return ApiResponse.success("密码修改成功", null);
    }
}
