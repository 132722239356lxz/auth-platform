<template>
  <view class="login-page">
    <view class="login-bg">
      <view class="logo-area">
        <view class="logo-icon">
          <text class="logo-text">Auth</text>
        </view>
        <text class="app-title">统一授权中台</text>
        <text class="app-subtitle">Unified Authorization Platform</text>
      </view>

      <view class="login-form">
        <view class="form-item">
          <input
            v-model="loginForm.account"
            class="form-input"
            type="text"
            maxlength="30"
            placeholder="手机号/用户名"
            placeholder-class="placeholder"
          />
        </view>
        <view class="form-item">
          <input
            v-model="loginForm.password"
            class="form-input"
            type="password"
            placeholder="请输入密码"
            placeholder-class="placeholder"
          />
        </view>

        <button class="login-btn" @tap="handleLogin" :loading="loading">
          登 录
        </button>

        <view class="login-switch">
          <text class="switch-text" @tap="switchLoginType">
            {{ loginType === 'password' ? '短信验证码登录' : '密码登录' }}
          </text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useUserStore } from '@/stores/user.js'
import { loginByPassword } from '@/api/auth.js'

const userStore = useUserStore()
const loading = ref(false)
const loginType = ref('password')

const loginForm = reactive({
  account: '',
  password: '',
})

async function handleLogin() {
  if (!loginForm.account || !loginForm.password) {
    uni.showToast({ title: '请输入手机号或用户名和密码', icon: 'none' })
    return
  }

  loading.value = true
  try {
    const account = loginForm.account
    const isPhone = /^1[3-9]\d{9}$/.test(account)
    const loginPayload = {
      password: loginForm.password,
      loginType: 'PASSWORD',
    }
    if (isPhone) {
      loginPayload.phone = account
    } else {
      loginPayload.username = account
    }

    const res = await loginByPassword(loginPayload)
    if (res.code === 200 && res.data) {
      await userStore.setLoginData(res.data)
      uni.showToast({ title: '登录成功', icon: 'success' })
      setTimeout(() => {
        uni.switchTab({ url: '/pages/index/index' })
      }, 500)
    } else {
      uni.showToast({ title: res.message || '登录失败', icon: 'none' })
    }
  } catch (e) {
    uni.showToast({ title: e.message || '登录失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function switchLoginType() {
  uni.showToast({ title: '短信验证码登录开发中', icon: 'none' })
}
</script>
</script>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40rpx;
}

.login-bg {
  width: 100%;
  max-width: 650rpx;
}

.logo-area {
  text-align: center;
  margin-bottom: 60rpx;
}

.logo-icon {
  width: 120rpx;
  height: 120rpx;
  border-radius: 30rpx;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 24rpx;
  backdrop-filter: blur(10px);
}

.logo-text {
  font-size: 40rpx;
  font-weight: 700;
  color: #fff;
}

.app-title {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: #fff;
  margin-bottom: 8rpx;
}

.app-subtitle {
  display: block;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.8);
}

.login-form {
  background: #fff;
  border-radius: 24rpx;
  padding: 40rpx;
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.1);
}

.form-item {
  margin-bottom: 24rpx;
}

.form-input {
  width: 100%;
  height: 88rpx;
  border: 2rpx solid #dcdfe6;
  border-radius: 12rpx;
  padding: 0 24rpx;
  font-size: 30rpx;
  box-sizing: border-box;
}

.placeholder {
  color: #c0c4cc;
}

.login-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background: #409eff;
  color: #fff;
  border: none;
  border-radius: 12rpx;
  font-size: 32rpx;
  font-weight: 600;
  margin-top: 16rpx;
}

.login-btn::after {
  border: none;
}

.login-switch {
  margin-top: 32rpx;
  text-align: center;
}

.switch-text {
  font-size: 26rpx;
  color: #409eff;
  text-decoration: underline;
}
</style>
