# 凭证轮换操作手册

当凭证泄露（误提交到仓库、日志外泄、人员变动等）时，按本手册执行轮换。

## 一、已发现的历史泄露凭证（必须轮换）

以下凭证曾以明文形式存在于版本库中，**默认视为已泄露，必须立即轮换**：

| 凭证 | 原存放位置 | 泄露值 | 影响 |
|---|---|---|---|
| OAuth2 门户客户端密钥 | `auth-server/application.yml` | `uz34ky*6EnNX$qHp` | 可伪造管理后台客户端换取 Token |
| Nacos 密码 | 根 `pom.xml`（dev/test/prod profile） | `2026@lxzgo` | 可登录 Nacos 篡改配置、接管服务注册 |
| 前端客户端密钥 | `frontend-web/.env.development` | `testdemo123` | 打进 JS 产物，等同公开 |
| 前端客户端密钥 | `frontend-web/.env.production` | `web-admin-secret` | 同上 |

> 说明：前端的两处密钥已随代码清理移除，且逻辑上公钥客户端本不应持有 `client_secret`，
> 授权码换取与 Token 刷新均已走服务端代理。

## 二、自动轮换（推荐）

```bash
bash scripts/security/rotate-secrets.sh
```

脚本会：备份旧 `.env` → 生成 8 项强随机凭证 → 写入 `.env`（权限 600）→
生成 `client_secret` 的 BCrypt 哈希 → 输出待执行的 SQL 与后续步骤。

## 三、人工确认清单

- [ ] **数据库**：执行脚本输出的 `UPDATE oauth2_registered_client ...` 更新 `admin-web` 密钥
- [ ] **Nacos 控制台**：修改 `nacos` 用户密码（与 `.env` 的 `NACOS_PASSWORD` 一致）
- [ ] **Nacos 鉴权密钥**：确认 `NACOS_AUTH_TOKEN` 已改为随机值（原为官方默认密钥，可伪造身份）
- [ ] **重启服务**：`docker compose -f docker-compose.prod.yml up -d --force-recreate`
- [ ] **验证**：登录流程、Token 刷新、Nacos 配置读取均正常
- [ ] **通知**：如凭证曾被多方知悉，通知相关方同步更新

## 四、清理 Git 历史中的旧凭证

仅删除文件**不能**消除历史记录中的明文，仍需清理历史。
以下操作会**重写提交历史**，务必先与团队协商并在维护窗口执行。

### 方式一：git filter-repo（推荐，需单独安装）

```bash
# 1. 全新克隆一份（保留原始仓库作为备份）
git clone --mirror <repo-url> auth-platform-mirror.git
cd auth-platform-mirror.git

# 2. 批量替换历史中的明文凭证
git filter-repo --replace-text ../replacements.txt

# 3. 强制推送到远端
git push --force --mirror
```

`replacements.txt` 内容（左侧为待替换的明文）：

```
uz34ky*6EnNX$qHp==>***REMOVED***
2026@lxzgo==>***REMOVED***
testdemo123==>***REMOVED***
web-admin-secret==>***REMOVED***
```

### 方式二：BFG Repo-Cleaner

```bash
bfg --replace-text replacements.txt auth-platform-mirror.git
cd auth-platform-mirror.git
git reflog expire --expire=now --all && git gc --prune=now --aggressive
git push --force
```

### 方式三：历史很短时的简易做法

若泄露发生在最近几次提交，可直接：

```bash
git reset --soft <泄露前的commit>
# 删除明文后重新提交
git push --force
```

### 清理后的收尾

```bash
# 通知所有协作者重新克隆（不要基于旧历史继续开发）
git clone <repo-url>
```

> ⚠️ 推送后，任何人若用旧历史继续开发并推送，会把旧凭证重新引入。

## 五、防止再次泄露

项目已内置以下防护，请勿绕过：

1. **CI 密钥扫描**（`.gitlab-ci.yml` 的 `secret-scan` 阶段）
   提交即扫描硬编码凭证，命中直接阻断流水线。本地自查：
   ```bash
   bash scripts/security/secret-scan.sh
   ```

2. **依赖漏洞扫描**（`dependency-scan` 阶段）
   基于 OWASP Dependency-Check 比对 NVD 库，识别含 CVE 的第三方组件。

3. **`.gitignore` 已忽略** `.env`、`*.pem`、`*.key` 等敏感文件。

4. **前端安全约定**
   - 任何 `VITE_` 前缀变量都会被打进 JS 产物，**禁止存放密钥**
   - 所有涉及 `client_secret` 的操作必须走服务端代理接口

## 六、轮换周期建议

| 凭证类型 | 建议周期 |
|---|---|
| OAuth2 客户端密钥 | 90 天，或人员变动时立即轮换 |
| 数据库 / Redis / MQ 密码 | 90 天 |
| Nacos 密码与 AUTH_TOKEN | 90 天 |
| JWT 签名密钥（JWK） | 180 天 |
| 第三方 API Key（AI/短信等） | 按供应商要求，建议 90 天 |
