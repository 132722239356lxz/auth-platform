<template>
  <div class="portal-login">
    <!-- 背景装饰 -->
    <div class="bg-decor">
      <div class="circle c1"></div>
      <div class="circle c2"></div>
      <div class="circle c3"></div>
    </div>

    <div class="login-wrapper">
      <!-- 左侧品牌区 -->
      <div class="brand-panel">
        <div class="brand-logo">
          <div class="logo-icon">
            <svg viewBox="0 0 64 64" fill="none">
              <rect x="4" y="16" width="56" height="36" rx="8" stroke="white" stroke-width="2.5"/>
              <path d="M16 16V8a8 8 0 0 1 8-8h16a8 8 0 0 1 8 8v8" stroke="white" stroke-width="2.5"/>
              <circle cx="22" cy="36" r="3" fill="white"/>
              <circle cx="42" cy="36" r="3" fill="white"/>
              <path d="M22 44h20" stroke="white" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </div>
          <h1>统一身份认证</h1>
          <p>Unified Identity Platform</p>
        </div>
        <div class="brand-features">
          <div class="feature-item">
            <div class="feature-icon">🔐</div>
            <span>一次登录，全平台通行</span>
          </div>
          <div class="feature-item">
            <div class="feature-icon">🛡️</div>
            <span>企业级安全认证</span>
          </div>
          <div class="feature-item">
            <div class="feature-icon">⚡</div>
            <span>极速接入，开箱即用</span>
          </div>
        </div>
      </div>

      <!-- 右侧登录表单 -->
      <div class="form-panel">
        <div class="form-card">
          <h2 class="form-title">欢迎回来</h2>
          <p class="form-subtitle">登录您的账号以访问业务系统</p>

          <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="rules"
            label-width="0"
            size="large"
            @keyup.enter="handleLogin"
          >
            <el-form-item prop="account">
              <el-input
                v-model="loginForm.account"
                placeholder="手机号/用户名"
                :prefix-icon="User"
                clearable
                class="custom-input"
              />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="密码"
                :prefix-icon="Lock"
                show-password
                class="custom-input"
              />
            </el-form-item>

            <div class="form-extra">
              <el-checkbox v-model="rememberMe">记住账号</el-checkbox>
              <el-link type="primary" :underline="false" @click="router.push('/register')">
                注册账号
              </el-link>
            </div>

            <el-button
              type="primary"
              size="large"
              class="login-btn"
              :loading="loading"
              @click="handleLogin"
            >
              {{ loading ? '登录中...' : '登 录' }}
            </el-button>
          </el-form>

          <div class="form-divider">
            <span>其他登录方式</span>
          </div>

          <div class="sso-area">
            <el-button class="sso-btn" @click="handleSSOLogin">
              <svg class="sso-icon" viewBox="0 0 24 24" fill="none" width="18" height="18">
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z" fill="#409eff"/>
              </svg>
              OAuth2 单点登录
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { encryptPassword } from '@/utils/crypto'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loginFormRef = ref<FormInstance>()
const loading = ref(false)
const rememberMe = ref(false)

const loginForm = reactive({ account: '', password: '' })

const rules: FormRules = {
  account: [{ required: true, message: '请输入手机号或用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

onMounted(() => {
  const saved = localStorage.getItem('portal_saved_account')
  if (saved) {
    loginForm.account = saved
    rememberMe.value = true
  }
})

async function handleLogin() {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      // 密码加密传输（RSA-OAEP）
      const encryptedPwd = await encryptPassword(loginForm.password)

      const account = loginForm.account
      const isPhone = /^1[3-9]\d{9}$/.test(account)
      const loginPayload: any = {
        password: encryptedPwd,
        loginType: 'PASSWORD',
        clientId: 'portal-web',
      }
      if (isPhone) {
        loginPayload.phone = account
      } else {
        loginPayload.username = account
      }

      const res = await login(loginPayload)
      if (res.code === 200 && res.data) {
        await userStore.setLoginData(res.data)
        if (rememberMe.value) {
          localStorage.setItem('portal_saved_account', loginForm.account)
        } else {
          localStorage.removeItem('portal_saved_account')
        }
        ElMessage.success(`欢迎回来，${res.data.user_info?.nickname || loginForm.account}`)

        // 若来自 OAuth2 授权流程（SAS 302 携带 ?sso=true），需续接流程获取授权码
        const isSsoFlow = route.query.sso === 'true'
        if (isSsoFlow) {
          // REST 登录已在服务端 Session 中保存了 SecurityContext，
          // 跳转到续接端点由服务端判断是回到 /oauth2/authorize 还是普通回调
          window.location.href = '/auth-server/login/oauth2-continue'
          return
        }

        router.push('/portal/home')
      } else {
        ElMessage.error(res.message || '登录失败')
      }
    } catch (e: any) {
      ElMessage.error(e?.message || '网络异常，请稍后重试')
    } finally {
      loading.value = false
    }
  })
}

function handleSSOLogin() {
  const clientId = import.meta.env.VITE_OAUTH_CLIENT_ID || 'web-admin'
  const redirectUri = import.meta.env.VITE_OAUTH_REDIRECT_URI || `${window.location.origin}/login/callback`
  const params = new URLSearchParams({
    response_type: 'code',
    client_id: clientId,
    scope: import.meta.env.VITE_OAUTH_SCOPES || 'openid profile',
    redirect_uri: redirectUri,
  })
  window.location.href = `/auth-server/oauth2/authorize?${params.toString()}`
}
</script>

<style scoped lang="scss">
.portal-login {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e9f2 50%, #dce3ed 100%);
  overflow: hidden;
}

.bg-decor {
  position: absolute;
  inset: 0;
  pointer-events: none;

  .circle {
    position: absolute;
    border-radius: 50%;
    opacity: 0.06;
    background: #409eff;
  }
  .c1 { width: 500px; height: 500px; top: -120px; left: -80px; }
  .c2 { width: 300px; height: 300px; bottom: -60px; right: -60px; }
  .c3 { width: 180px; height: 180px; top: 40%; right: 15%; }
}

.login-wrapper {
  position: relative;
  display: flex;
  width: 900px;
  max-width: 95vw;
  min-height: 560px;
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.08), 0 2px 12px rgba(0, 0, 0, 0.04);
  z-index: 1;
  background: #fff;
}

.brand-panel {
  flex: 1;
  background: linear-gradient(160deg, #1677ff 0%, #0958d9 40%, #002c8c 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 36px;
  color: #fff;
}

.brand-logo {
  text-align: center;
  margin-bottom: 48px;

  .logo-icon svg { width: 72px; height: 72px; }

  h1 {
    font-size: 26px;
    font-weight: 700;
    margin: 20px 0 6px;
    letter-spacing: 2px;
  }
  p {
    font-size: 13px;
    opacity: 0.7;
    letter-spacing: 1px;
  }
}

.brand-features {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  opacity: 0.9;
  padding: 10px 16px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  transition: background 0.3s;

  &:hover { background: rgba(255, 255, 255, 0.14); }

  .feature-icon {
    font-size: 20px;
    flex-shrink: 0;
  }
}

.form-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 40px;
  background: #fff;
}

.form-card {
  width: 100%;
  max-width: 340px;
}

.form-title {
  font-size: 24px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0 0 6px;
}

.form-subtitle {
  font-size: 13px;
  color: #909399;
  margin: 0 0 32px;
}

.custom-input {
  :deep(.el-input__wrapper) {
    border-radius: 10px;
    box-shadow: 0 0 0 1px #e4e7ed inset;
    transition: box-shadow 0.3s;
    &:hover, &.is-focus {
      box-shadow: 0 0 0 1px #409eff inset;
    }
  }
}

.form-extra {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  font-size: 13px;
}

.login-btn {
  width: 100%;
  height: 46px;
  border-radius: 10px;
  font-size: 16px;
  letter-spacing: 4px;
  background: linear-gradient(135deg, #1677ff, #409eff);
  border: none;

  &:hover {
    background: linear-gradient(135deg, #409eff, #69b1ff);
  }
}

.form-divider {
  display: flex;
  align-items: center;
  margin: 24px 0 16px;
  color: #c0c4cc;
  font-size: 12px;

  &::before, &::after {
    content: '';
    flex: 1;
    height: 1px;
    background: #ebeef5;
  }
  span { padding: 0 14px; }
}

.sso-area {
  display: flex;
  justify-content: center;
}

.sso-btn {
  width: 100%;
  height: 44px;
  border-radius: 10px;
  border: 1px solid #d9d9d9;
  font-size: 14px;
  color: #606266;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.3s;

  &:hover {
    border-color: #409eff;
    color: #409eff;
    background: #f0f6ff;
  }
}

@media (max-width: 768px) {
  .login-wrapper {
    flex-direction: column;
    width: 100vw;
    min-height: auto;
    border-radius: 0;
  }
  .brand-panel {
    padding: 32px 24px;
    .brand-features { display: none; }
  }
  .form-panel { padding: 32px 24px; }
}
</style>
