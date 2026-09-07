# CODEBUDDY.md This file provides guidance to CodeBuddy when working with code in this repository.

## Common Commands

```bash
# Install dependencies
npm install

# Start dev server (http://localhost:5173)
npm run dev

# Production build (type-check then bundle)
npm run build

# Preview production build
npm run preview
```

## Architecture Overview

This is the **Web management console** frontend for the 统一授权中台 (Unified Authorization Platform), built with Vue 3 + Vite + TypeScript + Element Plus. It provides a full admin dashboard for managing OAuth2 clients, users, roles, menus, workflows, messages, AI features, and audit logs. All backend services sit behind a Spring Cloud Gateway at port 8080; the Vite dev server proxies API calls to this gateway (or directly to individual services for debugging).

### Directory Purpose

| Directory | Purpose |
|---|---|
| `src/api/` | Axios API function definitions, one file per backend service domain |
| `src/stores/` | Pinia stores: `user` (auth state, permissions, menus) and `app` (sidebar collapse, unread count) |
| `src/router/` | Single file defining all static routes with permission guards |
| `src/layout/` | App shell with sidebar + navbar; components dynamically render based on router |
| `src/views/` | Page components organized by feature module (login, system, auth, workflow, message, ai, log, dashboard) |
| `src/components/` | Shared reusable components (currently empty; most UI uses Element Plus directly) |
| `src/utils/` | `request.ts` (Axios instance with interceptors) and `auth.ts` (JWT storage/parsing helpers) |
| `src/types/` | TypeScript interfaces for all API responses, domain models, and request params |
| `src/directives/` | `v-permission` directive for DOM-level permission-based show/hide |
| `src/styles/` | Global SCSS, variables, and utility classes |

### Request Flow & Auth

1. **`src/utils/request.ts`** creates a singleton Axios instance (`service`) with request/response interceptors. All API calls go through this instance via exported `get`, `post`, `put`, `del` helpers.

2. **Request interceptor**: Reads `access_token` from `localStorage` (key: `auth_access_token`) and attaches `Authorization: Bearer {token}`. For `/auth-flow/` endpoints, it also decodes the JWT to extract `sub` (username) and passes it as `X-User` header.

3. **Response interceptor**: Unwraps `ApiResponse<{code, message, data}>` — returns `data` on `code === 200`, triggers token refresh on `code === 401` or HTTP 401, shows `ElMessage.error` on other error codes. HTTP-level errors (403, 404, 500, 503) are also caught and surfaced via `ElMessage`.

4. **Token refresh** (`handleTokenExpired` in `request.ts`): On 401, calls `POST /auth-server/api/auth/refresh` with the stored `refresh_token`. If successful, updates stored tokens and retries all queued requests. If refresh fails or no refresh token exists, clears storage and redirects to `/login`.

### Authentication: Two Login Paths

The app supports two login modes on `/views/login/index.vue`:

- **SSO Login (OAuth2 Authorization Code)**: Redirects to `auth-server/oauth2/authorize` → user authenticates → callback to `/login/callback?code=xxx` → `callback.vue` calls `exchangeToken()` to POST `/auth-server/oauth2/token` with the code → stores token → fetches user info → redirects to `/dashboard`.

- **Password Login (Dev Mode)**: Calls `POST /auth-server/api/auth/login` with `{username, password, clientId}` directly. Receives `access_token`, `refresh_token`, `user_info`, and `permissions` in the response. Stores them and enters the app without OAuth2 redirects.

### State Management (Pinia)

**`useUserStore`** (`src/stores/user.ts`):
- `token` / `userInfo` / `permissions` / `menus` — reactive state
- `setToken()` — persists to localStorage via `utils/auth.ts`
- `fetchUserInfo()` — first parses JWT payload for quick info, then calls `getExtendedUserInfo()` and `getCurrentUserMenus()` APIs in parallel for complete data
- `hasPermission(perm)` — checks if user has a specific permission string; `*` is a wildcard super-admin
- `logout()` — clears all state and localStorage

**`useAppStore`** (`src/stores/app.ts`): Sidebar collapsed state and unread message badge count.

### Routing & Permission Guards

Routes are defined statically in `src/router/index.ts`. The guard (`router.beforeEach`) checks:
1. Public routes (`/login`, `/login/callback`) pass through.
2. No token → redirect to `/login`.
3. Has token but no `userInfo` in store → calls `fetchUserInfo()`.
4. Route has `meta.permission` → checks `userStore.hasPermission()`, redirects to `/dashboard` if denied.

Sidebar menus are built from the same `staticRoutes`, filtered by `meta.hidden` and user permissions.

### Backend Service Mapping

The Vite dev server proxies these path prefixes:

| Prefix | Default Target | Service |
|---|---|---|
| `/auth-server` | `http://127.0.0.1:9000` | OAuth2 auth, login, clients, audit, subsystem tokens |
| `/system-server` | `http://127.0.0.1:8080` | Users, roles, menus |
| `/auth-flow` | `http://127.0.0.1:8080` | Workflow approvals |
| `/auth-message` | `http://127.0.0.1:8080` | Message broadcasting |
| `/ai-agent-server` | `http://127.0.0.1:8080` | AI search, knowledge base, alerts |
| `/log-server` | `http://127.0.0.1:8080` | Log queries, AI log analysis |

The target can be overridden via `VITE_GATEWAY_URL` in `.env.development`. Note that `/auth-server` currently defaults to port 9000 (direct to auth-server) while others default to 8080 (through Gateway).

### API Response Formats

Two response envelope shapes exist in the codebase:
- **`ApiResponse<T>`** (`code`, `message`, `data`) — used by `auth-server` and `system-server`
- **`R<T>`** (`code`, `msg`, `data`) — used by `auth-flow` and `auth-message`

The response interceptor in `request.ts` handles both by checking `data.message || data.msg`.

### Permission System

Permissions are string identifiers (e.g., `system:user:list`, `sys:user:delete`). They come from:
- JWT `permissions` claim (parsed client-side for immediate use)
- `/api/userinfo/extended` API response (more authoritative)

Frontend enforcement:
- Route-level: `meta.permission` on route definitions checked by router guard
- DOM-level: `v-permission` directive (`src/directives/permission.ts`) removes elements if the user lacks the required permission
- Backend is the final authority — frontend checks are UX-only

### Key Types (`src/types/index.ts`)

- `UserInfo` — user model with `id`, `username`, `nickname`, `permissions`, `roles`
- `ClientInfo` — OAuth2 client registration fields
- `TokenInfo` / `LoginResponse` — auth token response structures
- `MenuInfo` — recursive menu tree (directory/menu/button types)
- `RoleInfo` — role with associated `menuIds`
- `WorkflowInstance` / `ApprovalRequest` — workflow state and actions
- `MessageInfo` — message center data
- `KnowledgeDoc` / `SearchResult` / `AlertInfo` — AI module types
- `LogRecord` — log entry model

### SCSS Conventions

Global variables in `src/styles/variables.scss` are auto-injected into every component's `<style lang="scss">` via Vite's `css.preprocessorOptions.scss.additionalData`. This means `$primary-color`, `$sidebar-width`, `$text-primary`, etc. are available in every `.vue` SFC without explicit imports.

Utility classes `.app-container`, `.filter-container`, `.pagination-container`, `.card-header` are defined in `src/styles/index.scss` for consistent page layouts.

### Environment Variables

`.env.development`: `VITE_GATEWAY_URL`, `VITE_OAUTH_CLIENT_ID`, `VITE_OAUTH_REDIRECT_URI`, `VITE_OAUTH_SCOPES`
`.env.production`: Same keys but gateway URL is empty (same-origin deployment assumed).

> **Security**: `VITE_OAUTH_CLIENT_SECRET` has been **removed**. Any `VITE_`-prefixed variable is
> inlined into the JS bundle and is therefore public. Exchanging the authorization code
> (`/api/auth/exchange-code`) and refreshing tokens (`/api/auth/refresh`) are both proxied by the
> backend, which holds the client secret in the `PORTAL_CLIENT_SECRET` environment variable.
> Never reintroduce a secret into front-end env files.

### Important Notes

- **`/api/auth/refresh`** endpoint is used for token refresh in the Axios interceptor. It expects `{refresh_token}` in the body and returns a full `LoginResponse` structure.
- **`auth.ts` utilities** manage localStorage keys: `auth_access_token`, `auth_refresh_token`, `auth_token_expire`, `auth_user_info`. Use `setTokens()` / `clearTokens()` rather than direct localStorage manipulation.
- **NProgress** is used for loading bar indication, started in request interceptor, stopped in response interceptor.
- **Element Plus** is installed globally with Chinese locale. All Element Plus icons are registered globally in `main.ts`.
- The sidebar menu uses `el-menu` with `router` mode — clicking menu items triggers Vue Router navigation directly.
