# Auth-Platform API 设计规范

## 统一返回格式

### system-server 模块
```json
{
  "code": 200,
  "message": "操作成功",
  "data": { }
}
```

使用 `ApiResponse<T>`:
- `ApiResponse.success(data)` - 成功
- `ApiResponse.success(message, data)` - 成功带消息
- `ApiResponse.fail(code, message)` - 失败

### 其他模块
```json
{
  "code": 200,
  "msg": "success",
  "data": { }
}
```

使用 `R<T>`:
- `R.ok(data)` - 成功
- `R.ok(message, data)` - 成功带消息
- `R.fail(code, message)` - 失败

## API 路径规范

| 模块 | 前缀 | 示例 |
|------|------|------|
| system-server | `/api/` | `/api/users`, `/api/roles` |
| auth-server | `/api/auth/` | `/api/auth/login` |
| auth-flow | `/api/flow/` | `/api/flow/workflows` |
| auth-message | `/api/msg/` | `/api/msg/records` |
| ai-agent-server | `/api/ai/` | `/api/ai/chat/ask` |
| log-server | `/api/log/` | `/api/log/query` |

## RESTful 方法规范

| 操作 | HTTP方法 | 路径示例 |
|------|---------|---------|
| 列表 | GET | `/api/users` |
| 分页 | GET | `/api/users/page` |
| 详情 | GET | `/api/users/{id}` |
| 创建 | POST | `/api/users` |
| 更新 | PUT | `/api/users/{id}` |
| 部分更新 | PATCH | `/api/users/{id}/enable` |
| 删除 | DELETE | `/api/users/{id}` |

## 分页参数

```json
{
  "page": 1,
  "pageSize": 10,
  "total": 100,
  "records": []
}
```

## 权限注解

```java
@RequirePermission("system:user:list")    // 列表权限
@RequirePermission("system:user:edit")    // 编辑权限
@RequirePermission("system:user:delete")  // 删除权限
```

## 公开接口

```java
@PublicApi(description = "健康检查接口")
@GetMapping("/health")
public R<String> health() { }
```
