package com.liang.xz.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * <p>系统管理服务启动类 —— 角色/菜单/权限管理 + 用户管理</p>
 *
 * <p>职责:</p>
 * <ul>
 *   <li>用户管理: 用户CRUD、启用/禁用</li>
 *   <li>角色管理: 角色创建/编辑/删除/分配权限</li>
 *   <li>菜单管理: 动态菜单树、按钮权限</li>
 *   <li>权限管理: 权限标识维护</li>
 *   <li>JWT认证: 作为资源服务器，从Token中解析用户信息</li>
 * </ul>
 *
 * <p>与auth-server的关系:</p>
 * <pre>
 *   auth-server (授权服务器)     system-server (系统管理)
 *   ┌──────────────────────┐    ┌──────────────────────┐
 *   │ 单点登录 /oauth2/**    │    │ 用户管理 /api/users/** │
 *   │ Token签发 /oauth2/token│   │ 角色管理 /api/roles/** │
 *   │ 登录页 /login          │    │ 菜单管理 /api/menus/** │
 *   │ 用户注册 /api/register │    │ 权限管理 /api/perms/** │
 *   └──────────────────────┘    └──────────────────────┘
 *              ↓ JWT Token
 *      ┌───────────────┴───────────────┐
 *      │  Gateway (统一入口)             │
 *      │  /system/** → system-server    │
 *      │  /auth/**   → auth-server      │
 *      └───────────────────────────────┘
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = "com.liang.xz")
@EnableDiscoveryClient
public class SystemServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SystemServerApplication.class, args);
    }
}
