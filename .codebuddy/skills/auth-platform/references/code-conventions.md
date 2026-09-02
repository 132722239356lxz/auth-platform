# Auth-Platform 编码规范

## 1. 实体类规范

### 必须规则
- 使用 Lombok: `@Data @Builder @NoArgsConstructor @AllArgsConstructor`
- 每个字段添加 `@Schema(description = "...")` 注解
- 类注释必须包含映射的数据库表名
- 类注释使用 JavaDoc 格式: `@author auth-platform`, `@since x.y.z`

### 示例
```java
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {

    @Schema(description = "用户主键(自增)")
    private Long id;

    @Schema(description = "用户名(登录账号，唯一)")
    private String username;
}
```

## 2. Service 接口规范

### 必须规则
- 面向接口编程: Service 必须定义接口 `IXxxService`，实现类为 `XxxService`
- 接口放在同一包下
- Controller 注入接口类型

### 示例
```java
// 接口定义
public interface IUserManageService {
    List<UserResponse> listAll();
    UserResponse create(UserCreateRequest request);
}

// 实现类
@Service
@RequiredArgsConstructor
public class UserManageService implements IUserManageService {
    // ...
}

// Controller
@RestController
public class UserController {
    private final IUserManageService userManageService; // 注入接口
}
```

## 3. MyBatis Mapper 接口规范

### 必须规则
- Mapper 接口放在 `mapper/` 包下
- 使用 `@Mapper` 注解标注
- 方法参数使用 `@Param` 注解
- XML 映射文件放在 `resources/mapper/` 下，与接口同名

### 示例
```java
@Mapper
public interface UserMapper {
    List<UserEntity> findByQuery(@Param("keyword") String keyword,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);
}
```

## 4. DTO 规范

- 请求对象: `XxxRequest`
- 响应对象: `XxxResponse`
- 分页查询: `XxxPageQuery`
- 所有字段添加 `@Schema` 注解和 `@NotNull`/`@NotBlank` 验证注解

## 5. Controller 规范

- 使用 `@Tag(name = "...")` 描述控制器
- 使用 `@Operation(summary = "...")` 描述接口
- 使用 `@Parameter` 描述参数
- 统一返回 `ApiResponse<T>` 或 `R<T>`
- 权限控制使用 `@RequirePermission("system:user:list")`

## 6. Repository 规范（JdbcTemplate）

- 使用 `@Repository` 注解
- 注入 `NamedParameterJdbcTemplate` 或 `JdbcTemplate`
- 手写 SQL，使用 `MapSqlParameterSource` 传参
- RowMapper 使用私有静态内部类

## 7. 命名规范

| 类型 | 格式 | 示例 |
|------|------|------|
| 实体 | `XxxEntity` | `UserEntity` |
| DTO请求 | `XxxRequest` | `UserCreateRequest` |
| DTO响应 | `XxxResponse` | `UserResponse` |
| Service接口 | `IXxxService` | `IUserManageService` |
| Service实现 | `XxxService` | `UserManageService` |
| Mapper接口 | `XxxMapper` | `UserMapper` |
| Repository | `XxxRepository` | `UserRepository` |
| Controller | `XxxController` | `UserController` |
| 配置类 | `XxxConfig` | `MybatisPlusConfig` |
