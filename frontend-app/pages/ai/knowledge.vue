<template>
  <view class="page">
    <!-- 知识库列表 -->
    <view class="card">
      <text class="text-bold text-lg mb-20">知识库列表</text>
      <view class="kb-list" v-if="knowledgeBases.length">
        <view :class="['kb-item', activeKb === kb.name ? 'active' : '']" v-for="kb in knowledgeBases" :key="kb.name" @tap="selectKb(kb.name)">
          <text class="kb-name">{{ kb.name }}</text>
          <text class="kb-count text-sm text-info">{{ kb.docCount || 0 }} 篇</text>
        </view>
      </view>
      <view class="empty-inline" v-else>
        <text class="text-sm text-info">暂无知识库</text>
      </view>
    </view>

    <!-- 文档列表 -->
    <view class="card" v-if="docs.length">
      <text class="text-bold text-lg mb-20">{{ activeKb ? '知识库文档' : '全部文档' }}</text>
      <view class="doc-list">
        <view class="doc-item" v-for="doc in docs" :key="doc.id">
          <view class="flex-between">
            <text class="doc-title text-bold">{{ doc.title }}</text>
            <text :class="['doc-status', docStatusClass(doc.status)]">{{ doc.status || '处理中' }}</text>
          </view>
          <view class="doc-meta mt-10">
            <text class="text-sm text-info">知识库: {{ doc.kbName }}</text>
            <text class="text-sm text-info" style="margin-left: 16rpx">分块: {{ doc.chunkCount || 0 }}</text>
            <text class="text-sm text-info" style="margin-left: 16rpx">{{ doc.contentType || 'TEXT' }}</text>
          </view>
        </view>
      </view>
    </view>

    <view class="empty" v-if="!loading && !docs.length">
      <text class="empty-text">暂无文档</text>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getKnowledgeBases, getKnowledgeDocs, getAllDocs } from '@/api/ai.js'

const loading = ref(false)
const knowledgeBases = ref([])
const docs = ref([])
const activeKb = ref('')

async function loadData() {
  loading.value = true
  try {
    const res = await getKnowledgeBases()
    knowledgeBases.value = res?.data || []

    const docRes = activeKb.value
      ? await getKnowledgeDocs(activeKb.value)
      : await getAllDocs()
    docs.value = docRes?.data || []
  } catch (e) {
    docs.value = []
  } finally {
    loading.value = false
  }
}

async function selectKb(name) {
  activeKb.value = name
  loading.value = true
  try {
    const res = await getKnowledgeDocs(name)
    docs.value = res?.data || []
  } catch (e) {
    docs.value = []
  } finally {
    loading.value = false
  }
}

function docStatusClass(status) {
  return status === 'READY' ? 'status-ready' : 'status-processing'
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.page { min-height: 100vh; padding: 20rpx; padding-bottom: 120rpx; }
.kb-list { display: flex; flex-direction: column; gap: 12rpx; }
.kb-item { display: flex; justify-content: space-between; align-items: center; padding: 20rpx; border: 2rpx solid #e4e7ed; border-radius: 12rpx; }
.kb-item.active { border-color: #409eff; background: #ecf5ff; }
.kb-name { font-size: 30rpx; color: #303133; }
.doc-list { display: flex; flex-direction: column; gap: 16rpx; }
.doc-item { padding: 20rpx; border-bottom: 1rpx solid #f0f0f0; }
.doc-item:last-child { border-bottom: none; }
.doc-title { font-size: 28rpx; color: #303133; }
.doc-meta { display: flex; flex-wrap: wrap; }
.doc-status { font-size: 22rpx; padding: 2rpx 12rpx; border-radius: 6rpx; }
.status-ready { background: #f0f9eb; color: #67c23a; }
.status-processing { background: #fdf6ec; color: #e6a23c; }
.empty-inline { text-align: center; padding: 20rpx 0; }
.empty { text-align: center; padding: 80rpx 0; }
.empty-text { color: #909399; font-size: 28rpx; }
</style>
