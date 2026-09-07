<template>
  <view class="page">
    <view class="card" v-if="msg">
      <text class="msg-title text-lg text-bold">{{ msg.title }}</text>
      <view class="msg-meta mt-10">
        <text class="text-sm text-info">类型: {{ msg.messageType || '通知' }}</text>
        <text class="text-sm text-info" style="margin-left: 20rpx">{{ msg.createdAt || '' }}</text>
      </view>
      <view class="msg-tags mt-10" v-if="msg.channels && msg.channels.length">
        <text class="msg-tag" v-for="c in msg.channels" :key="c">{{ c }}</text>
      </view>
      <view class="msg-divider"></view>
      <text class="msg-body">{{ msg.content }}</text>
    </view>

    <view class="empty" v-if="!loading && !msg">
      <text class="empty-text">消息不存在</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { getMessageDetail, markAsRead } from '@/api/message.js'

const loading = ref(false)
const msg = ref(null)

async function loadData(id) {
  loading.value = true
  try {
    const res = await getMessageDetail(id)
    msg.value = res?.data || null
    if (msg.value && !msg.value.read) {
      try { await markAsRead(id) } catch (e) {}
    }
  } catch (e) {
    uni.showToast({ title: '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

import { onLoad } from '@dcloudio/uni-app'
onLoad((options) => {
  if (options.id) loadData(options.id)
})
</script>

<style scoped lang="scss">
.page { min-height: 100vh; padding: 20rpx; }
.msg-title { display: block; }
.msg-meta { display: flex; align-items: center; }
.msg-tags { display: flex; gap: 8rpx; }
.msg-tag { font-size: 22rpx; padding: 2rpx 12rpx; border-radius: 6rpx; background: #f4f4f5; color: #909399; }
.msg-divider { height: 1rpx; background: #f0f0f0; margin: 20rpx 0; }
.msg-body { font-size: 30rpx; line-height: 1.8; color: #303133; }
.empty { text-align: center; padding: 120rpx 0; }
.empty-text { color: #909399; font-size: 28rpx; }
</style>
