package com.liang.xz.system.service;

import com.liang.xz.system.dto.CurrentUserResponse;

/**
 * <p>当前用户服务接口</p>
 *
 * @author auth-platform
 * @since 1.0.0
 */
public interface ICurrentUserService {

    /**
     * 获取当前登录用户详细信息
     */
    CurrentUserResponse getCurrentUser();
}
