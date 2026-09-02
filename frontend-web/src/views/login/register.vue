<template>
  <div class="register-container">
    <div class="register-bg-shapes">
      <div class="shape shape-1"></div>
      <div class="shape shape-2"></div>
      <div class="shape shape-3"></div>
    </div>
    <div class="register-box">
      <div class="register-header">
        <div class="register-logo">
          <div class="logo-icon">
            <svg viewBox="0 0 48 48" fill="none"><rect width="48" height="48" rx="12" fill="rgba(255,255,255,0.2)"/><path d="M24 12L36 18V30L24 36L12 30V18L24 12Z" stroke="white" stroke-width="2" stroke-linejoin="round"/><circle cx="24" cy="24" r="4" fill="white"/></svg>
          </div>
        </div>
        <h1 class="register-title">创建账号</h1>
        <p class="register-subtitle">注册后即可使用统一授权平台</p>
      </div>

      <div class="register-card">
        <el-form ref="registerFormRef" :model="registerForm" :rules="rules" label-width="0" size="large">
          <el-form-item prop="phone">
            <el-input
              v-model="registerForm.phone"
              placeholder="请输入手机号"
              :prefix-icon="Phone"
              clearable
            />
          </el-form-item>
          <el-form-item prop="username">
            <el-input
              v-model="registerForm.username"
              placeholder="请输入用户名（选填）"
              :prefix-icon="User"
              clearable
            />
          </el-form-item>
          <el-form-item prop="nickname">
            <el-input
              v-model="registerForm.nickname"
              placeholder="请输入昵称（选填）"
              :prefix-icon="UserFilled"
              clearable
            />
          </el-form-item>
          <el-form-item prop="email">
            <el-input
              v-model="registerForm.email"
              placeholder="请输入邮箱（选填）"
              :prefix-icon="Message"
              clearable
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="registerForm.password"
              type="password"
              placeholder="请输入密码（6-32位）"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item prop="confirmPassword">
            <el-input
              v-model="registerForm.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              :prefix-icon="Lock"
              show-password
              @keyup.enter="handleRegister"
            />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              size="large"
              class="register-btn"
              :loading="loading"
              @click="handleRegister"
            >
              {{ loading ? '注册中...' : '注 册' }}
            </el-button>
          </el-form-item>
        </el-form>

        <div class="register-extra">
          <span>已有账号？</span>
          <el-link type="primary" @click="$router.push('/login')">立即登录</el-link>
        </div>
      </div>

      <div class="register-footer">
        <p>Copyright &copy; 2026 Auth Platform</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Phone, User, UserFilled, Lock, Message } from '@element-plus/icons-vue'
import { registerUser } from '@/api/auth'

const router = useRouter()
const registerFormRef = ref<FormInstance>()
const loading = ref(false)

const registerForm = reactive({
  phone: '',
  username: '',
  nickname: '',
  email: '',
  password: '',
  confirmPassword: '',
})

const validateConfirm = (_rule: any, value: string, callback: any) => {
  if (!value) {
    callback(new Error('请再次输入密码'))
  } else if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' },
  ],
  username: [
    { min: 3, max: 30, message: '用户名长度3-30个字符', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]*$/, message: '用户名只能包含字母、数字和下划线', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度6-32位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, validator: validateConfirm, trigger: 'blur' },
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
}

async function handleRegister() {
  if (!registerFormRef.value) return
  await registerFormRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      const res = await registerUser({
        phone: registerForm.phone,
        username: registerForm.username || undefined,
        password: registerForm.password,
        nickname: registerForm.nickname || undefined,
        email: registerForm.email || undefined,
      })

      if (res.code === 200) {
        ElMessage.success('注册成功！即将跳转到登录页...')
        setTimeout(() => {
          router.push('/login')
        }, 1500)
      } else {
        ElMessage.error(res.message || '注册失败')
      }
    } catch (error: any) {
      ElMessage.error(error?.message || '网络异常，请稍后重试')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped lang="scss">
.register-container {
  position: relative;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  overflow: hidden;
}

.register-bg-shapes {
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

.shape-1 { width: 600px; height: 600px; top: -200px; right: -100px; }
.shape-2 { width: 400px; height: 400px; bottom: -100px; left: -50px; }
.shape-3 { width: 200px; height: 200px; top: 50%; left: 60%; }

.register-box {
  position: relative;
  width: 420px;
  max-width: 90vw;
  z-index: 1;
}

.register-header {
  text-align: center;
  margin-bottom: 24px;
}

.register-logo { margin-bottom: 12px; }

.logo-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  svg { width: 52px; height: 52px; }
}

.register-title {
  color: #fff;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 2px;
  margin: 0 0 6px;
}

.register-subtitle {
  color: rgba(255, 255, 255, 0.55);
  font-size: 13px;
  margin: 0;
}

.register-card {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 12px;
  padding: 32px 28px 20px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.register-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  letter-spacing: 4px;
  margin-top: 4px;
}

.register-extra {
  text-align: center;
  margin-top: 12px;
  font-size: 14px;
  color: #909399;
}

.register-footer {
  text-align: center;
  margin-top: 28px;
  color: rgba(255, 255, 255, 0.4);
  font-size: 12px;
}
</style>
