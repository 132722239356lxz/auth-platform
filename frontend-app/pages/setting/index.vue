<template>
  <view class="page">
    <!-- 用户信息 -->
    <view class="user-header">
      <view class="user-avatar">{{ userStore.nickname?.charAt(0)?.toUpperCase() || 'U' }}</view>
      <view class="user-meta">
        <text class="user-name text-bold">{{ userStore.nickname || '用户' }}</text>
        <text class="user-id text-sm text-info">ID: {{ userStore.userInfo?.id || '-' }}</text>
      </view>
    </view>

    <!-- 信息卡片 -->
    <view class="card">
      <view class="info-row" v-for="info in userInfos" :key="info.label">
        <text class="info-label">{{ info.label }}</text>
        <text class="info-value">{{ info.value }}</text>
      </view>
    </view>

    <!-- 权限列表 -->
    <view class="card" v-if="userStore.permissions && userStore.permissions.length">
      <text class="text-bold text-lg mb-20">权限列表</text>
      <view class="perm-list">
        <text class="perm-tag" v-for="p in userStore.permissions" :key="p">{{ p }}</text>
      </view>
    </view>

    <!-- 功能列表 -->
    <view class="card">
      <view class="menu-item" v-for="item in menus" :key="item.label" @tap="item.action">
        <text class="menu-label">{{ item.label }}</text>
        <text class="menu-arrow">></text>
      </view>
    </view>

    <!-- 退出登录 -->
    <button class="logout-btn" @tap="handleLogout">退出登录</button>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '@/stores/user.js'

const userStore = useUserStore()

const userInfos = computed(() => [
  { label: '用户名', value: userStore.username || '-' },
  { label: '昵称', value: userStore.nickname || '-' },
  { label: '用户类型', value: userStore.userInfo?.userType || '普通用户' },
  { label: '租户ID', value: userStore.userInfo?.tenantId || 'default' }
])

const menus = [
  { label: '清除缓存', action: () => {
    uni.showModal({
      title: '提示',
      content: '确定清除缓存吗？',
      success: (res) => {
        if (res.confirm) {
          uni.clearStorageSync()
          uni.showToast({ title: '缓存已清除', icon: 'success' })
        }
      }
    })
  }},
  { label: '关于系统', action: () => {
    uni.showModal({
      title: '关于',
      content: '统一授权中台 v1.0.0\nSpring Authorization Server + SpringCloudAlibaba\nVue3 + UniApp',
      showCancel: false
    })
  }}
]

function handleLogout() {
  uni.showModal({
    title: '提示',
    content: '确定要退出登录吗？',
    success: (res) => {
      if (res.confirm) {
        userStore.logout()
        uni.reLaunch({ url: '/pages/login/index' })
      }
    }
  })
}
</script>

<style scoped lang="scss">
.page { min-height: 100vh; padding: 20rpx; padding-bottom: 120rpx; }
.user-header {
  display: flex;
  align-items: center;
  background: linear-gradient(135deg, #409eff, #667eea);
  border-radius: 20rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
}
.user-avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
  font-size: 40rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 24rpx;
}
.user-meta { display: flex; flex-direction: column; }
.user-name { color: #fff; font-size: 34rpx; }
.user-id { color: rgba(255, 255, 255, 0.8); margin-top: 4rpx; }
.info-row { display: flex; justify-content: space-between; padding: 16rpx 0; border-bottom: 1rpx solid #f0f0f0; }
.info-row:last-child { border-bottom: none; }
.info-label { color: #909399; font-size: 28rpx; }
.info-value { color: #303133; font-size: 28rpx; }
.perm-list { display: flex; flex-wrap: wrap; gap: 8rpx; }
.perm-tag { font-size: 22rpx; padding: 4rpx 16rpx; border-radius: 6rpx; background: #ecf5ff; color: #409eff; }
.menu-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #f0f0f0;
}
.menu-item:last-child { border-bottom: none; }
.menu-label { font-size: 30rpx; color: #303133; }
.menu-arrow { color: #c0c4cc; font-size: 32rpx; }
.logout-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background: #f56c6c;
  color: #fff;
  border: none;
  border-radius: 12rpx;
  font-size: 32rpx;
  margin-top: 24rpx;
}
.logout-btn::after { border: none; }
</style>
