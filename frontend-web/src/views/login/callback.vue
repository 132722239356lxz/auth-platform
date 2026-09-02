<template>
  <div class="callback-container">
    <el-card class="callback-card" shadow="always">
      <div v-if="loading" class="callback-content">
        <el-icon class="loading-icon"><Loading /></el-icon>
        <p class="callback-text">{{ statusText }}</p>
      </div>

      <div v-else-if="success" class="callback-content">
        <el-icon class="success-icon"><CircleCheckFilled /></el-icon>
        <p class="callback-text">登录成功，正在跳转...</p>
      </div>

      <div v-else class="callback-content">
        <el-icon class="error-icon"><CircleCloseFilled /></el-icon>
        <p class="callback-text">{{ errorText }}</p>
        <el-button type="primary" @click="router.push('/adminLogin')">返回管理后台登录</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading, CircleCheckFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import { exchangeCode } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const success = ref(false)
const statusText = ref('正在处理登录回调...')
const errorText = ref('')

onMounted(async () => {
  const code = route.query.code as string
  const error = route.query.error as string

  if (error) {
    loading.value = false
    errorText.value = `授权失败: ${error}`
    return
  }

  if (!code) {
    loading.value = false
    errorText.value = '未收到授权码'
    return
  }

  try {
    statusText.value = '正在换取 Token...'

    // state 即子系统的回调地址，作为 exchange-code 的 redirectUri
    const redirectUri = route.query.state as string
    // 使用安全端点：由服务端代理调用 /oauth2/token，client_secret 不暴露在前端
    const tokenInfo = await exchangeCode(code, redirectUri, redirectUri)

    if (tokenInfo?.access_token) {
      userStore.setToken(
        tokenInfo.access_token,
        tokenInfo.refresh_token,
        tokenInfo.expires_in
      )

      statusText.value = '正在获取用户信息...'
      await userStore.fetchUserInfo()

      success.value = true
      loading.value = false
      ElMessage.success('登录成功')

      setTimeout(() => {
        // SSO 回调按角色跳转：管理员 → 后台管理，普通用户 → 用户门户
        const isAdmin = userStore.isAdmin
        router.push(isAdmin ? '/dashboard' : '/portal/home')
      }, 500)
    } else {
      throw new Error('Token 获取失败')
    }
  } catch (error: any) {
    loading.value = false
    errorText.value = error?.message || 'Token 交换失败，请重试'
    ElMessage.error('登录失败: ' + (error?.message || '未知错误'))
  }
})
</script>

<style scoped lang="scss">
.callback-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.callback-card {
  width: 400px;
  text-align: center;
}

.callback-content {
  padding: 40px 20px;
}

.loading-icon {
  font-size: 48px;
  color: #409eff;
  animation: rotate 1.5s linear infinite;
}

.success-icon {
  font-size: 48px;
  color: #67c23a;
}

.error-icon {
  font-size: 48px;
  color: #f56c6c;
}

.callback-text {
  margin: 16px 0;
  font-size: 16px;
  color: #606266;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
