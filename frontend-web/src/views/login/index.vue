<template>
  <div class="login-container">
    <div class="login-bg-shapes">
      <div class="shape shape-1"></div>
      <div class="shape shape-2"></div>
      <div class="shape shape-3"></div>
    </div>
    <div class="login-box">
      <div class="login-header">
        <div class="login-logo">
          <div class="logo-icon">
            <svg viewBox="0 0 48 48" fill="none"><rect width="48" height="48" rx="12" fill="rgba(255,255,255,0.2)"/><path d="M24 12L36 18V30L24 36L12 30V18L24 12Z" stroke="white" stroke-width="2" stroke-linejoin="round"/><circle cx="24" cy="24" r="4" fill="white"/></svg>
          </div>
        </div>
        <h1 class="login-title">统一授权中台</h1>
        <p class="login-subtitle">Unified Authorization Platform · 管理后台</p>
      </div>

      <div class="login-card">
        <el-tabs v-model="activeTab" class="login-tabs">
          <!-- 密码登录 -->
          <el-tab-pane label="密码登录" name="password">
            <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" label-width="0" size="large">
              <el-form-item prop="account">
                <el-input
                  v-model="loginForm.account"
                  placeholder="手机号/用户名"
                  :prefix-icon="User"
                  clearable
                />
              </el-form-item>
              <el-form-item prop="password">
                <el-input
                  v-model="loginForm.password"
                  type="password"
                  placeholder="请输入密码"
                  :prefix-icon="Lock"
                  show-password
                  @keyup.enter="handleLogin"
                />
              </el-form-item>
              <el-form-item>
                <el-button
                  type="primary"
                  size="large"
                  class="login-btn"
                  :loading="loading"
                  @click="handleLogin"
                >
                  {{ loading ? '登录中...' : '登 录' }}
                </el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <!-- SSO 登录 -->
          <el-tab-pane label="SSO 登录" name="sso">
            <div class="sso-login">
              <div class="sso-info">
                <el-icon :size="40" color="#409eff"><Connection /></el-icon>
                <p>通过 OAuth2 授权码模式进行统一身份认证</p>
              </div>
              <el-button
                type="primary"
                size="large"
                class="sso-btn"
                @click="handleSSOLogin"
              >
                <el-icon><Right /></el-icon>
                前往 SSO 登录
              </el-button>
            </div>
          </el-tab-pane>
        </el-tabs>

        <div class="login-extra">
          <el-checkbox v-model="rememberMe">记住账号</el-checkbox>
        </div>
      </div>

      <div class="login-footer">
        <p>Copyright &copy; 2026 Auth Platform &middot; Spring Authorization Server</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Right, Connection } from '@element-plus/icons-vue'
import { login } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { encryptPassword } from '@/utils/crypto'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const activeTab = ref('password')
const loginFormRef = ref<FormInstance>()
const loading = ref(false)
const rememberMe = ref(false)
/** 是否从 SSO / OAuth2 授权码流程重定向而来 */
const isSSOContext = ref(false)

const loginForm = reactive({
  account: '',
  password: '',
})

const loginRules: FormRules = {
  account: [{ required: true, message: '请输入手机号或用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

onMounted(() => {
  const savedAccount = localStorage.getItem('admin_saved_account')
  if (savedAccount) {
    loginForm.account = savedAccount
    rememberMe.value = true
  }

  // 检测是否从 OAuth2 授权码流程重定向而来
  if (route.query.error || route.query.sso !== undefined || route.query.logout) {
    isSSOContext.value = true
  }
})

/** 密码登录 */
async function handleLogin() {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      console.log('[Login] 开始密码加密...')
      // 密码加密传输（RSA-OAEP）
      const encryptedPwd = await encryptPassword(loginForm.password)
      console.log('[Login] 加密完成，开始发送登录请求...')

      const account = loginForm.account
      const isPhone = /^1[3-9]\d{9}$/.test(account)
      const loginPayload: any = {
        password: encryptedPwd,
        loginType: 'PASSWORD',
      }
      if (isPhone) {
        loginPayload.phone = account
      } else {
        loginPayload.username = account
      }

      const res = await login(loginPayload)

      console.log('[Login] 登录响应:', res.code)

      if (res.code === 200 && res.data) {
        const data = res.data
        console.log('[Login] 登录数据:', {
          hasToken: !!data.access_token,
          roles: data.roles,
          userType: data.user_info?.userType,
          permissions: (data.permissions || []).slice(0, 5),
        })

        await userStore.setLoginData(data)

        console.log('[Login] setLoginData 完成, userInfo:', {
          roleCodes: userStore.userInfo?.roleCodes,
          userType: userStore.userInfo?.userType,
          permissionsCount: userStore.permissions?.length,
          isAdmin: userStore.isAdmin,
        })

        // 非管理员（普通用户）不允许登录后台管理系统
        if (!userStore.isAdmin) {
          const roleInfo = (userStore.userInfo?.roleCodes || []).join(',') || '(无角色)'
          const ut = userStore.userInfo?.userType || '(无)'
          console.warn('[Login] 登录被拒绝: 非管理员用户, roles=' + roleInfo + ', userType=' + ut)
          userStore.logout()
          ElMessage.error(`普通用户无法登录后台管理系统 (角色: ${roleInfo})`)
          loading.value = false
          return
        }

        if (rememberMe.value) {
          localStorage.setItem('admin_saved_account', loginForm.account)
        } else {
          localStorage.removeItem('admin_saved_account')
        }

        const nickName = data.user_info?.nickname || data.user_info?.username || ''
        ElMessage.success(`欢迎回来，${nickName}`)

        // SSO 上下文: 登录完成后自动跳转到 OAuth2 授权码流程
        if (isSSOContext.value) {
          ElMessage.info('已认证成功，正在跳转授权页面...')
          handleSSOLogin()
          return
        }

        // 管理员登录 → 后台管理仪表盘
        router.push('/dashboard')
      } else {
        ElMessage.error(res.message || '登录失败')
      }
    } catch (error: any) {
      console.error('[Login] 登录失败:', error)
      // 如果是 fetch/axios 被浏览器扩展拦截导致的错误，给出提示
      if (error?.message?.includes('isM3U8Video') || error?.message?.includes('content.js')) {
        ElMessage.warning('检测到浏览器扩展干扰，建议禁用视频下载类扩展后重试')
      } else {
        ElMessage.error(error?.message || '网络异常，请稍后重试')
      }
    } finally {
      loading.value = false
    }
  })
}

/** SSO 登录 — OAuth2 授权码模式 */
function handleSSOLogin() {
  const clientId = import.meta.env.VITE_OAUTH_CLIENT_ID || 'web-admin'
  const redirectUri = import.meta.env.VITE_OAUTH_REDIRECT_URI || `${window.location.origin}/login/callback`
  const scopes = import.meta.env.VITE_OAUTH_SCOPES || 'openid profile'

  const params = new URLSearchParams({
    response_type: 'code',
    client_id: clientId,
    scope: scopes,
    redirect_uri: redirectUri,
  })

  window.location.href = `/auth-server/oauth2/authorize?${params.toString()}`
}
</script>

<style scoped lang="scss">
.login-container {
  position: relative;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  overflow: hidden;
}

.login-bg-shapes {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.shape {
  position: absolute;
  border-radius: 50%;
  opacity: 0.06;
  background: #fff;
}

.shape-1 {
  width: 600px;
  height: 600px;
  top: -200px;
  right: -100px;
}

.shape-2 {
  width: 400px;
  height: 400px;
  bottom: -100px;
  left: -50px;
}

.shape-3 {
  width: 200px;
  height: 200px;
  top: 50%;
  left: 60%;
}

.login-box {
  position: relative;
  width: 420px;
  max-width: 90vw;
  z-index: 1;
}

.login-header {
  text-align: center;
  margin-bottom: 28px;
}

.login-logo {
  margin-bottom: 16px;
}

.logo-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;

  svg {
    width: 56px;
    height: 56px;
  }
}

.login-title {
  color: #fff;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 3px;
  margin: 0 0 8px;
}

.login-subtitle {
  color: rgba(255, 255, 255, 0.5);
  font-size: 12px;
  letter-spacing: 1px;
  margin: 0;
}

.login-card {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 12px;
  padding: 32px 28px 20px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(10px);
}

.login-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 24px;
  }

  :deep(.el-tabs__item) {
    font-size: 15px;
    font-weight: 500;
  }
}

.sso-login {
  text-align: center;
  padding: 20px 0;
}

.sso-info {
  margin-bottom: 24px;
  color: #606266;
  font-size: 14px;
  line-height: 1.8;

  .el-icon {
    margin-bottom: 12px;
  }
}

.sso-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  letter-spacing: 4px;
  margin-top: 4px;
}

.login-extra {
  display: flex;
  justify-content: center;
  align-items: center;
  margin-top: 12px;
  padding: 0 4px;
}

.login-footer {
  text-align: center;
  margin-top: 32px;
  color: rgba(255, 255, 255, 0.4);
  font-size: 12px;
}
</style>
