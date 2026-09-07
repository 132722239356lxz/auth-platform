<template>
  <view class="page">
    <view class="list" v-if="list.length">
      <view class="card msg-item" v-for="item in list" :key="item.messageId" @tap="goDetail(item)">
        <view class="flex-between">
          <text class="msg-title text-bold" :class="{ 'unread': !item.read }">{{ item.title }}</text>
          <text class="text-sm text-info">{{ item.createdAt || '' }}</text>
        </view>
        <text class="msg-content text-sm text-info">{{ item.content }}</text>
        <view class="msg-tags mt-10">
          <text class="msg-tag" v-if="item.messageType">{{ item.messageType }}</text>
          <text class="msg-tag" v-for="c in (item.channels || [])" :key="c">{{ c }}</text>
          <text :class="['msg-tag', item.read ? 'tag-read' : 'tag-unread']">{{ item.read ? '已读' : '未读' }}</text>
        </view>
      </view>
    </view>

    <view class="empty" v-if="!loading && !list.length">
      <text class="empty-text">暂无消息</text>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getInbox, getUnreadCount } from '@/api/message.js'
import { useUserStore } from '@/stores/user.js'

const userStore = useUserStore()
const loading = ref(false)
const list = ref([])

async function loadData() {
  loading.value = true
  try {
    const username = userStore.username || 'admin'
    const res = await getInbox(username, 50)
    list.value = res?.data || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function goDetail(item) {
  uni.navigateTo({ url: `/pages/message/detail?id=${item.messageId}` })
}

onShow(loadData)
</script>

<style scoped lang="scss">
.page { min-height: 100vh; padding: 20rpx; padding-bottom: 120rpx; }
.msg-item { margin-bottom: 16rpx; }
.msg-title { color: #303133; font-size: 30rpx; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-right: 16rpx; }
.unread { font-weight: 700; }
.msg-content { display: block; margin-top: 8rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.msg-tags { display: flex; gap: 8rpx; flex-wrap: wrap; }
.msg-tag { font-size: 22rpx; padding: 2rpx 12rpx; border-radius: 6rpx; background: #f4f4f5; color: #909399; }
.tag-read { background: #f4f4f5; color: #909399; }
.tag-unread { background: #fef0f0; color: #f56c6c; }
.empty { text-align: center; padding: 120rpx 0; }
.empty-text { color: #909399; font-size: 28rpx; }
</style>
