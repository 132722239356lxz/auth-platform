import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import axios from 'axios'
import { getAccessToken, setTokens, clearTokens, setUserInfo, getUserInfoFromStorage, parseJwt } from '@/utils/auth'
import { getCurrentUser, getCurrentUserMenus } from '@/api/system'
import { setLoginInitializing } from '@/utils/request'
import type { UserInfo, MenuInfo, LoginResponse, CurrentUserResponse, ApiResponse } from '@/types'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getAccessToken() || '')
  const userInfo = ref<UserInfo | null>(getUserInfoFromStorage())
  const permissions = ref<string[]>([])
  const menus = ref<MenuInfo[]>([])

  const isLoggedIn = computed(() => !!token.value)
  const username = computed(() => userInfo.value?.username || '')
  const nickname = computed(() => userInfo.value?.nickname || userInfo.value?.username || '')
  /**
   * 是否为管理员（可登录后台管理系统）。
   *
   * <p>与后端 TokenIssuerService.isRegularUser() 对齐：
   * 仅当用户所有角色编码均为 ROLE_USER（或未分配角色）时视为普通用户拦截，
   * 只要拥有任意一个非 ROLE_USER 的角色即可登录后台。</p>
   */
  const isAdmin = computed(() => {
    const codes = userInfo.value?.roleCodes
    // 有角色编码数据时，按后端逻辑判断：至少有一个非 ROLE_USER 角色即可
    if (codes && codes.length > 0) {
      return codes.some(code => code !== 'ROLE_USER')
    }
    // 没有角色编码数据时（如登录刚完成尚未拉取），用 userType / permissions 兜底
    return userInfo.value?.userType === 'admin' || permissions.value.includes('*')
  })

  /** 设置 Token */
  function setToken(accessToken: string, refreshToken?: string, expiresIn?: number) {
    token.value = accessToken
    setTokens(accessToken, refreshToken, expiresIn)
  }

  /** 从登录响应直接填充用户信息并加载菜单 */
  async function setLoginData(data: LoginResponse) {
    // ★ 开启初始化标记，防止拦截器 401 处理在此期间误清 token
    setLoginInitializing(true)

    try {
      token.value = data.access_token
      setTokens(data.access_token, data.refresh_token, data.expires_in)

      // 记录登录响应中的角色信息，后续 getCurrentUser 可能覆盖
      const loginRoles: string[] = data.roles || []
      console.log('[UserStore] 登录响应 roles:', loginRoles, ', userType:', data.user_info?.userType)

      userInfo.value = {
        id: Number(data.user_info?.userId) || 0,
        username: data.user_info?.username || '',
        nickname: data.user_info?.nickname || '',
        userType: data.user_info?.userType || '',
        tenantId: data.user_info?.tenantId || '',
        enabled: true,
        permissions: data.permissions || [],
        roleCodes: loginRoles,
      }
      permissions.value = data.permissions || []
      setUserInfo(userInfo.value)

      // 登录后立即拉取菜单 + 角色编码（侧边栏依赖菜单，isAdmin 判断依赖 roleCodes）
      // ★ 使用原始 axios + 手动 Authorization 头，绕过请求/响应拦截器的 401 处理。
      //    避免拦截器在登录初始化阶段因为 system-server 返回 401 而误清 token。
      const authHeader = { Authorization: `Bearer ${data.access_token}` }

      // 并行请求：拉取用户完整信息 + 菜单
      const [userResult, menuResult] = await Promise.allSettled([
        axios.get('/system-server/api/users/current', { headers: authHeader }),
        getCurrentUserMenusInLogin(authHeader),
      ])

      // 处理 getCurrentUser 响应
      if (userResult.status === 'fulfilled') {
        const respData = userResult.value.data as ApiResponse<CurrentUserResponse>
        if (respData?.code === 200 && respData?.data) {
          const d = respData.data
          console.log('[UserStore] getCurrentUser 响应 roles:', d.roles, ', userType:', d.userType)
          const mergedRoles = (d.roles && d.roles.length > 0) ? d.roles : loginRoles
          userInfo.value = {
            ...userInfo.value,
            username: d.username || userInfo.value?.username || '',
            nickname: d.nickname || userInfo.value?.nickname || '',
            userType: d.userType || userInfo.value?.userType || '',
            tenantId: d.tenantId || userInfo.value?.tenantId || '',
            deptId: d.deptId || userInfo.value?.deptId,
            deptName: d.deptName || userInfo.value?.deptName,
            roleCodes: mergedRoles,
            permissions: d.permissions || permissions.value,
          }
          if (d.permissions) {
            permissions.value = d.permissions
          }
          setUserInfo(userInfo.value)
        } else {
          console.warn('[UserStore] getCurrentUser 业务异常:', respData?.code, respData?.message)
        }
      } else {
        console.warn('[UserStore] getCurrentUser 请求失败（网络/Auth）:', userResult.reason)
      }

      // 处理菜单响应
      if (menuResult.status === 'fulfilled') {
        const menuData = menuResult.value
        if (menuData && Array.isArray(menuData)) {
          menus.value = menuData
        }
      } else {
        console.warn('[UserStore] 加载菜单失败:', menuResult.reason)
      }

      // ★ 关键防护：如果拦截器的 401 处理意外清除了 token，立即恢复
      if (!getAccessToken()) {
        console.warn('[UserStore] ⚠️ Token 被误清除，正在恢复...')
        setTokens(data.access_token, data.refresh_token, data.expires_in)
      }

      console.log('[UserStore] 最终 roleCodes:', userInfo.value?.roleCodes, 'isAdmin:', isAdmin.value)
    } finally {
      // ★ 无论成功或失败，都要清除初始化标记
      setLoginInitializing(false)
    }
  }

  /** 登录初始化时，用原始 axios 拉取菜单（绕过拦截器） */
  async function getCurrentUserMenusInLogin(authHeader: Record<string, string>): Promise<MenuInfo[]> {
    try {
      const resp = await axios.get('/system-server/api/users/current/menus', { headers: authHeader })
      const respData = resp.data as ApiResponse<MenuInfo[]>
      if (respData?.code === 200 && respData?.data) {
        return respData.data
      }
    } catch {
      // 菜单加载失败不阻塞登录
    }
    return []
  }

  /** 加载用户菜单（独立方法，登录后 / 页面刷新后均可调用） */
  async function fetchMenus() {
    try {
      const menuResp = await getCurrentUserMenus()
      if (menuResp?.data) {
        menus.value = menuResp.data
      }
    } catch {
      console.warn('加载菜单失败，侧边栏将为空')
    }
  }

  /** 获取用户信息 */
  async function fetchUserInfo() {
    try {
      // 先从 JWT 中解析基本信息
      const jwt = parseJwt(token.value)
      if (jwt) {
        userInfo.value = {
          id: Number(jwt.user_id) || 0,
          username: jwt.sub || '',
          nickname: jwt.nickname || jwt.sub || '',
          userType: jwt.user_type,
          tenantId: jwt.tenant_id,
          permissions: jwt.permissions || [],
          enabled: true,
        }
        permissions.value = jwt.permissions || []
        setUserInfo(userInfo.value)
      }

      // 再从 API 获取完整信息（system-server 拥有 RBAC 数据，比 auth-server 更准确）
      const [extResp, menuResp] = await Promise.allSettled([
        getCurrentUser(),
        getCurrentUserMenus(),
      ])

      if (extResp.status === 'fulfilled' && extResp.value?.data) {
        const data = extResp.value.data
        userInfo.value = {
          ...userInfo.value,
          id: data.userId || userInfo.value?.id || 0,
          username: data.username || userInfo.value?.username || '',
          nickname: data.nickname || userInfo.value?.nickname || '',
          userType: data.userType || userInfo.value?.userType || '',
          tenantId: data.tenantId || userInfo.value?.tenantId || '',
          deptId: data.deptId || userInfo.value?.deptId,
          deptName: data.deptName || userInfo.value?.deptName,
          roleCodes: data.roles || userInfo.value?.roleCodes || [],
          permissions: data.permissions || permissions.value,
          enabled: true,
        }
        permissions.value = userInfo.value?.permissions || []
        setUserInfo(userInfo.value)
      }

      if (menuResp.status === 'fulfilled' && menuResp.value?.data) {
        menus.value = menuResp.value.data
      }
    } catch (error) {
      console.error('获取用户信息失败:', error)
    }
  }

  /** 检查权限 */
  function hasPermission(perm: string): boolean {
    if (!perm) return true
    if (permissions.value.includes('*')) return true
    // 超级管理员拥有所有权限
    if (userInfo.value?.userType === 'admin') return true
    // 仅 ROLE_ADMIN 角色拥有所有权限（与后端 PermissionAspect 的 ROLE_ADMIN 逻辑对齐）
    if (userInfo.value?.roleCodes?.includes('ROLE_ADMIN')) return true
    return permissions.value.includes(perm)
  }

  /** 登出 */
  function logout() {
    token.value = ''
    userInfo.value = null
    permissions.value = []
    menus.value = []
    clearTokens()
  }

  return {
    token,
    userInfo,
    permissions,
    menus,
    isLoggedIn,
    username,
    nickname,
    isAdmin,
    setToken,
    setLoginData,
    fetchUserInfo,
    fetchMenus,
    hasPermission,
    logout,
  }
})
