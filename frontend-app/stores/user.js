import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getAccessToken, setTokens, clearTokens, setUserInfo, getUserInfoFromStorage, parseJwt } from '@/utils/auth.js'
import { getExtendedUserInfo } from '@/api/auth.js'

export const useUserStore = defineStore('user', () => {
  const token = ref(getAccessToken())
  const userInfo = ref(getUserInfoFromStorage())
  const permissions = ref([])

  const isLoggedIn = computed(() => !!token.value)
  const username = computed(() => userInfo.value?.username || '')
  const nickname = computed(() => userInfo.value?.nickname || userInfo.value?.username || '用户')

  function setToken(accessToken, refreshToken, expiresIn) {
    token.value = accessToken
    setTokens(accessToken, refreshToken, expiresIn)
  }

  /**
   * 登录成功后直接从响应填充用户信息
   * @param {object} data 登录响应 data
   */
  function setLoginData(data) {
    token.value = data.access_token
    setTokens(data.access_token, data.refresh_token, data.expires_in)

    userInfo.value = {
      id: Number(data.user_info?.userId) || 0,
      username: data.user_info?.username || '',
      nickname: data.user_info?.nickname || '',
      userType: data.user_info?.userType || '',
      tenantId: data.user_info?.tenantId || '',
      enabled: true,
      permissions: data.permissions || [],
      roleCodes: data.roles || [],
    }
    permissions.value = data.permissions || []
    setUserInfo(userInfo.value)
  }

  async function fetchUserInfo() {
    try {
      const jwt = parseJwt(token.value)
      if (jwt) {
        userInfo.value = {
          id: Number(jwt.user_id) || 0,
          username: jwt.sub || '',
          nickname: jwt.nickname || jwt.sub || '',
          userType: jwt.user_type,
          tenantId: jwt.tenant_id,
          permissions: jwt.permissions || [],
          enabled: true
        }
        permissions.value = jwt.permissions || []
        setUserInfo(userInfo.value)
      }

      try {
        const res = await getExtendedUserInfo()
        if (res?.data) {
          userInfo.value = { ...userInfo.value, ...res.data }
          permissions.value = res.data.permissions || permissions.value
          setUserInfo(userInfo.value)
        }
      } catch (e) {
        // API 不可用时使用 JWT 中的信息
      }
    } catch (e) {
      console.error('获取用户信息失败:', e)
    }
  }

  function hasPermission(perm) {
    if (!perm) return true
    if (permissions.value.includes('*')) return true
    return permissions.value.includes(perm)
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    permissions.value = []
    clearTokens()
  }

  return {
    token, userInfo, permissions,
    isLoggedIn, username, nickname,
    setToken, setLoginData, fetchUserInfo, hasPermission, logout
  }
})
