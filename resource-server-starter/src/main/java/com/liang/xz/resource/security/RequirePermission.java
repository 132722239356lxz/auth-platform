package com.liang.xz.resource.security;

import java.lang.annotation.*;

/**
 * <p>菜单权限注解 —— 标注在Controller方法上，声明该方法需要的权限标识</p>
 *
 * <p>权限校验链路: 用户 → 角色 → 菜单(权限标识)</p>
 *
 * <p>使用示例:</p>
 * <pre>
 *   // 单个权限
 *   {@code @RequirePermission("system:user:list")}
 *   public ApiResponse list() { ... }
 *
 *   // 多个权限(OR逻辑，满足任一即可)
 *   {@code @RequirePermission({"system:user:add", "system:user:edit"})}
 *   public ApiResponse save() { ... }
 *
 *   // 多个权限(AND逻辑，全部满足)
 *   {@code @RequirePermission(value = {"system:user:list", "system:role:list"}, logical = Logical.AND)}
 *   public ApiResponse dashboard() { ... }
 * </pre>
 *
 * <p>业务模块使用方式:</p>
 * <pre>
 *   // 1. pom.xml 引入 resource-server-starter
 *   // 2. Controller 方法上添加注解即可
 * </pre>
 *
 * @author auth-platform
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    /**
     * 权限标识(对应 sys_menu.permission 字段)
     * <p>如: system:user:list, system:role:add</p>
     */
    String[] value() default {};

    /**
     * 多个权限时的逻辑关系
     * <ul>
     *   <li>OR: 满足任一权限即可访问(默认)</li>
     *   <li>AND: 必须满足所有权限才可访问</li>
     * </ul>
     */
    Logical logical() default Logical.OR;

    /**
     * 权限逻辑关系
     */
    enum Logical {
        /** 满足任一即可 */
        OR,
        /** 必须全部满足 */
        AND
    }
}
