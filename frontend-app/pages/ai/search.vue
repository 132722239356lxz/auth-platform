<template>
  <view class="page">
    <!-- 搜索框 -->
    <view class="search-bar">
      <input
        v-model="query"
        class="search-input"
        type="text"
        placeholder="输入搜索内容..."
        confirm-type="search"
        @confirm="handleSearch"
      />
      <view class="search-type">
        <picker :range="typeNames" :range-key="'label'" @change="onTypeChange">
          <view class="type-text">{{ typeNames[typeIndex].label }}</view>
        </picker>
      </view>
      <button class="search-btn" @tap="handleSearch" :loading="loading">搜索</button>
    </view>

    <!-- 搜索结果 -->
    <view class="results" v-if="results.length">
      <view class="result-meta">
        <text class="text-sm text-info">找到 {{ results.length }} 条结果 · {{ latency }}ms</text>
      </view>
      <view class="card result-item" v-for="(item, idx) in results" :key="idx">
        <view class="flex-between">
          <text class="result-title text-bold">{{ item.title || '未命名' }}</text>
          <text class="text-sm text-success" v-if="item.score">{{ (item.score * 100).toFixed(0) }}%</text>
        </view>
        <text class="result-content text-sm">{{ item.content }}</text>
        <text class="result-source text-sm text-primary" v-if="item.source">{{ item.source }}</text>
      </view>
    </view>

    <view class="empty" v-if="!loading && searched && !results.length">
      <text class="empty-text">未找到相关结果</text>
    </view>

    <!-- 搜索建议 -->
    <view class="card" v-if="suggestions.length">
      <text class="text-bold mb-10">相关建议</text>
      <view class="suggestions">
        <text class="suggestion-tag" v-for="s in suggestions" :key="s" @tap="query = s; handleSearch()">{{ s }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { localSearch, webSearch, ragSearch, hybridSearch } from '@/api/ai.js'

const query = ref('')
const loading = ref(false)
const searched = ref(false)
const results = ref([])
const suggestions = ref([])
const latency = ref(0)
const typeIndex = ref(0)

const typeNames = [
  { label: '本地', value: 'LOCAL' },
  { label: '联网', value: 'INTERNET' },
  { label: 'RAG', value: 'RAG' },
  { label: '混合', value: 'HYBRID' }
]

function onTypeChange(e) {
  typeIndex.value = e.detail.value
}

async function handleSearch() {
  if (!query.value.trim()) return
  loading.value = true
  searched.value = true
  results.value = []

  const start = Date.now()
  try {
    const type = typeNames[typeIndex.value].value
    let res
    switch (type) {
      case 'LOCAL': res = await localSearch(query.value, 10); break
      case 'INTERNET': res = await webSearch(query.value, 10); break
      case 'RAG': res = await ragSearch(query.value, 5); break
      case 'HYBRID': res = await hybridSearch(query.value, 10); break
    }
    latency.value = Date.now() - start

    if (res?.data) {
      if (Array.isArray(res.data)) {
        results.value = res.data
      } else if (res.data.results) {
        results.value = res.data.results
      } else if (res.data.hits) {
        results.value = res.data.hits
      }
    }
  } catch (e) {
    results.value = []
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.page { min-height: 100vh; padding: 20rpx; padding-bottom: 120rpx; }
.search-bar { display: flex; gap: 12rpx; margin-bottom: 20rpx; align-items: center; }
.search-input { flex: 1; height: 72rpx; border: 2rpx solid #dcdfe6; border-radius: 12rpx; padding: 0 20rpx; font-size: 28rpx; background: #fff; box-sizing: border-box; }
.search-type { background: #fff; border: 2rpx solid #dcdfe6; border-radius: 12rpx; padding: 0 16rpx; height: 72rpx; display: flex; align-items: center; }
.type-text { font-size: 26rpx; color: #409eff; white-space: nowrap; }
.search-btn { height: 72rpx; line-height: 72rpx; background: #409eff; color: #fff; border: none; border-radius: 12rpx; font-size: 28rpx; padding: 0 28rpx; }
.search-btn::after { border: none; }
.result-meta { margin-bottom: 12rpx; }
.result-item { margin-bottom: 16rpx; }
.result-title { font-size: 30rpx; color: #303133; }
.result-content { display: block; margin-top: 8rpx; color: #606266; line-height: 1.6; }
.result-source { display: block; margin-top: 8rpx; }
.suggestions { display: flex; flex-wrap: wrap; gap: 12rpx; }
.suggestion-tag { font-size: 26rpx; padding: 8rpx 20rpx; border-radius: 20rpx; background: #ecf5ff; color: #409eff; }
.empty { text-align: center; padding: 120rpx 0; }
.empty-text { color: #909399; font-size: 28rpx; }
</style>
