<template>
  <view class="home-page">
    <!-- 用户信息卡片 -->
    <view class="user-card">
      <view class="user-avatar">{{ userStore.nickname?.charAt(0)?.toUpperCase() || 'U' }}</view>
      <view class="user-info">
        <text class="user-name">{{ userStore.nickname || '用户' }}</text>
        <text class="user-role">{{ userStore.userInfo?.userType || '普通用户' }}</text>
      </view>
    </view>

    <!-- 统计卡片 -->
    <view class="stats-grid">
      <view class="stat-item" v-for="item in stats" :key="item.label" @tap="item.action && navigateTo(item.action)">
        <view class="stat-icon" :style="{ backgroundColor: item.color }">
          <text class="stat-icon-text">{{ item.icon }}</text>
        </view>
        <text class="stat-value">{{ item.value }}</text>
        <text class="stat-label">{{ item.label }}</text>
      </view>
    </view>

    <!-- 功能入口 -->
    <view class="section">
      <text class="section-title">快捷功能</text>
      <view class="func-grid">
        <view class="func-item" v-for="func in functions" :key="func.label" @tap="navigateTo(func.path)">
          <view class="func-icon" :style="{ backgroundColor: func.bgColor }">
            <text class="func-icon-text">{{ func.icon }}</text>
          </view>
          <text class="func-label">{{ func.label }}</text>
        </view>
      </view>
    </view>

    <!-- 系统信息 -->
    <view class="section">
      <text class="section-title">系统信息</text>
      <view class="info-card">
        <view class="info-row" v-for="info in systemInfo" :key="info.label">
          <text class="info-label">{{ info.label }}</text>
          <text class="info-value">{{ info.value }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useUserStore } from '@/stores/user.js'
import { getAuditDashboard, getSubsystemStats } from '@/api/auth.js'
import { getMyPending } from '@/api/workflow.js'
import { getUnreadCount } from '@/api/message.js'

const userStore = useUserStore()

const stats = reactive([
  { label: '待审批', value: 0, color: '#e6a23c', icon: '审', action: '/pages/workflow/list' },
  { label: '未读消息', value: 0, color: '#f56c6c', icon: '消', action: '/pages/message/inbox' },
  { label: '活跃Token', value: 0, color: '#409eff', icon: '令' },
  { label: '客户端', value: 0, color: '#67c23a', icon: '端' }
])

const functions = [
  { label: '发起申请', icon: '请', path: '/pages/workflow/create', bgColor: '#409eff' },
  { label: '审批列表', icon: '批', path: '/pages/workflow/list', bgColor: '#67c23a' },
  { label: '消息中心', icon: '信', path: '/pages/message/inbox', bgColor: '#e6a23c' },
  { label: 'AI搜索', icon: '搜', path: '/pages/ai/search', bgColor: '#f56c6c' },
  { label: '知识库', icon: '知', path: '/pages/ai/knowledge', bgColor: '#909399' },
  { label: '设置', icon: '设', path: '/pages/setting/index', bgColor: '#9c27b0' }
])

const systemInfo = [
  { label: '授权框架', value: 'Spring Auth Server' },
  { label: '微服务', value: 'Spring Cloud Alibaba' },
  { label: '注册中心', value: 'Nacos 2.3.2' },
  { label: '消息队列', value: 'RabbitMQ' },
  { label: 'AI引擎', value: 'OpenAI + Lucene' }
]

function navigateTo(url) {
  uni.navigateTo({ url })
}

onMounted(async () => {
  if (!userStore.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }

  // 加载数据
  const [pendingRes, unreadRes, statsRes, dashboardRes] = await Promise.allSettled([
    getMyPending(userStore.username),
    getUnreadCount(userStore.username),
    getSubsystemStats(),
    getAuditDashboard()
  ])

  if (pendingRes.status === 'fulfilled' && pendingRes.value?.data) {
    stats[0].value = Array.isArray(pendingRes.value.data) ? pendingRes.value.data.length : 0
  }
  if (unreadRes.status === 'fulfilled' && unreadRes.value?.data != null) {
    stats[1].value = unreadRes.value.data
  }
  if (statsRes.status === 'fulfilled' && statsRes.value?.data) {
    stats[2].value = statsRes.value.data.activeCount || 0
  }
  if (dashboardRes.status === 'fulfilled' && dashboardRes.value?.data) {
    stats[3].value = dashboardRes.value.data.totalClients || 0
  }
})
</script>

<style scoped lang="scss">
.home-page {
  min-height: 100vh;
  padding: 20rpx;
  padding-bottom: 120rpx;
}

.user-card {
  display: flex;
  align-items: center;
  background: linear-gradient(135deg, #409eff, #667eea);
  border-radius: 20rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
}

.user-avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
  font-size: 36rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 24rpx;
}

.user-info {
  display: flex;
  flex-direction: column;
}

.user-name {
  color: #fff;
  font-size: 34rpx;
  font-weight: 600;
}

.user-role {
  color: rgba(255, 255, 255, 0.8);
  font-size: 24rpx;
  margin-top: 4rpx;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16rpx;
  margin-bottom: 32rpx;
}

.stat-item {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.stat-icon {
  width: 60rpx;
  height: 60rpx;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8rpx;
}

.stat-icon-text {
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
}

.stat-value {
  font-size: 36rpx;
  font-weight: 700;
  color: #303133;
}

.stat-label {
  font-size: 22rpx;
  color: #909399;
  margin-top: 4rpx;
}

.section {
  margin-bottom: 32rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16rpx;
  display: block;
}

.func-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16rpx;
}

.func-item {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.func-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12rpx;
}

.func-icon-text {
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
}

.func-label {
  font-size: 24rpx;
  color: #606266;
}

.info-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.info-row {
  display: flex;
  justify-content: space-between;
  padding: 12rpx 0;
  border-bottom: 1rpx solid #f0f0f0;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  font-size: 28rpx;
  color: #909399;
}

.info-value {
  font-size: 28rpx;
  color: #303133;
}
</style>
