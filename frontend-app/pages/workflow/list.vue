<template>
  <view class="page">
    <!-- Tab 切换 -->
    <view class="tabs">
      <view :class="['tab-item', activeTab === 'applications' ? 'active' : '']" @tap="switchTab('applications')">我的申请</view>
      <view :class="['tab-item', activeTab === 'pending' ? 'active' : '']" @tap="switchTab('pending')">待我审批</view>
    </view>

    <!-- 列表 -->
    <view class="list" v-if="list.length">
      <view class="card list-item" v-for="item in list" :key="item.id" @tap="goDetail(item.id)">
        <view class="flex-between">
          <text class="item-title text-bold text-lg">{{ item.title }}</text>
          <view :class="['status-tag', statusClass(item.status)]">{{ statusText(item.status) }}</view>
        </view>
        <view class="item-meta mt-10">
          <text class="text-sm text-info">申请人: {{ item.applicant }}</text>
          <text class="text-sm text-info" style="margin-left: 20rpx">{{ item.createdAt || '' }}</text>
        </view>
        <view class="mt-10" v-if="item.currentNodeName">
          <text class="text-sm text-primary">当前节点: {{ item.currentNodeName }}</text>
        </view>
      </view>
    </view>

    <!-- 空状态 -->
    <view class="empty" v-if="!loading && !list.length">
      <text class="empty-text">暂无数据</text>
    </view>

    <!-- 加载中 -->
    <view class="loading" v-if="loading">
      <text class="loading-text">加载中...</text>
    </view>

    <!-- 浮动按钮 -->
    <view class="fab" @tap="goCreate">
      <text class="fab-text">+</text>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getMyApplications, getMyPending } from '@/api/workflow.js'
import { useUserStore } from '@/stores/user.js'

const userStore = useUserStore()
const activeTab = ref('applications')
const loading = ref(false)
const list = ref([])

function statusText(status) {
  return { PENDING: '审批中', APPROVED: '已通过', REJECTED: '已驳回', WITHDRAWN: '已撤回' }[status] || status
}

function statusClass(status) {
  return { PENDING: 'status-warning', APPROVED: 'status-success', REJECTED: 'status-danger', WITHDRAWN: 'status-info' }[status] || 'status-info'
}

async function loadData() {
  loading.value = true
  try {
    const username = userStore.username || 'admin'
    const res = activeTab.value === 'applications'
      ? await getMyApplications(username)
      : await getMyPending(username)
    list.value = res?.data || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function switchTab(tab) {
  activeTab.value = tab
  loadData()
}

function goDetail(id) {
  uni.navigateTo({ url: `/pages/workflow/detail?id=${id}` })
}

function goCreate() {
  uni.navigateTo({ url: '/pages/workflow/create' })
}

onShow(loadData)
</script>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  padding: 20rpx;
  padding-bottom: 120rpx;
}

.tabs {
  display: flex;
  background: #fff;
  border-radius: 12rpx;
  margin-bottom: 20rpx;
  overflow: hidden;
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 24rpx 0;
  font-size: 30rpx;
  color: #606266;
  position: relative;
}

.tab-item.active {
  color: #409eff;
  font-weight: 600;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 30%;
  right: 30%;
  height: 4rpx;
  background: #409eff;
  border-radius: 2rpx;
}

.list-item {
  margin-bottom: 16rpx;
}

.item-title {
  color: #303133;
  flex: 1;
}

.status-tag {
  padding: 4rpx 16rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
}

.status-success { background: #f0f9eb; color: #67c23a; }
.status-warning { background: #fdf6ec; color: #e6a23c; }
.status-danger { background: #fef0f0; color: #f56c6c; }
.status-info { background: #f4f4f5; color: #909399; }

.empty, .loading {
  text-align: center;
  padding: 80rpx 0;
}

.empty-text, .loading-text {
  color: #909399;
  font-size: 28rpx;
}

.fab {
  position: fixed;
  right: 40rpx;
  bottom: 160rpx;
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: #409eff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 16rpx rgba(64, 158, 255, 0.4);
}

.fab-text {
  color: #fff;
  font-size: 48rpx;
}
</style>
