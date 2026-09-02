<template>
  <div class="app-container">
    <!-- 筛选区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="filter" size="default">
        <el-form-item label="关键词">
          <el-input v-model="filter.keyword" placeholder="标题/内容搜索" clearable @keyup.enter="handleSearch" style="width: 200px" />
        </el-form-item>
        <el-form-item label="来源系统">
          <el-input v-model="filter.sourceSystem" placeholder="子系统标识" clearable @keyup.enter="handleSearch" style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filter.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="发送中" value="SENDING" />
            <el-option label="已发送" value="SENT" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="filter.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            clearable
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 事件列表 -->
    <el-card shadow="never">
      <el-table :data="events" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="sourceSystem" label="来源系统" width="140">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ row.sourceSystem || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="事件标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="targetSubsystems" label="目标子系统" width="160">
          <template #default="{ row }">
            {{ row.targetSubsystems || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleView(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="filter.page"
          v-model:page-size="filter.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="事件详情" width="600px">
      <el-descriptions v-if="currentEvent" :column="2" border>
        <el-descriptions-item label="事件ID" :span="2">{{ currentEvent.messageId }}</el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ currentEvent.title }}</el-descriptions-item>
        <el-descriptions-item label="来源系统">{{ currentEvent.sourceSystem }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(currentEvent.status)">{{ statusText(currentEvent.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="目标子系统" :span="2">{{ currentEvent.targetSubsystems || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(currentEvent.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="内容" :span="2">
          <div style="white-space: pre-wrap; max-height: 200px; overflow-y: auto">{{ currentEvent.content }}</div>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { queryMessages } from '@/api/message'
import type { MessageInfo } from '@/types'

const loading = ref(false)
const events = ref<MessageInfo[]>([])
const total = ref(0)
const detailVisible = ref(false)
const currentEvent = ref<MessageInfo | null>(null)

const filter = reactive({
  keyword: '',
  sourceSystem: '',
  status: '',
  timeRange: [] as string[],
  page: 1,
  size: 20
})

function statusText(s?: string) {
  return { PENDING: '待处理', SENDING: '发送中', SENT: '已发送', FAILED: '失败' }[s || ''] || s || '-'
}

function statusTag(s?: string) {
  return { PENDING: 'warning', SENDING: '', SENT: 'success', FAILED: 'danger' }[s || ''] as any || 'info'
}

function formatTime(time?: string) {
  if (!time) return '-'
  return time.replace('T', ' ').slice(0, 19)
}

async function loadData() {
  loading.value = true
  try {
    const res = await queryMessages({
      keyword: filter.keyword || undefined,
      messageType: 'EVENT_PUSH',
      status: filter.status || undefined,
      receiver: filter.sourceSystem || undefined,
      startTime: filter.timeRange?.[0] || undefined,
      endTime: filter.timeRange?.[1] || undefined,
      page: filter.page,
      size: filter.size
    })
    const data = res?.data
    events.value = data?.list || []
    total.value = data?.total || 0
  } catch {
    events.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  filter.page = 1
  loadData()
}

function resetFilter() {
  filter.keyword = ''
  filter.sourceSystem = ''
  filter.status = ''
  filter.timeRange = []
  filter.page = 1
  filter.size = 20
  loadData()
}

function handleView(row: MessageInfo) {
  currentEvent.value = row
  detailVisible.value = true
}

onMounted(loadData)
</script>

<style scoped>
.filter-card { margin-bottom: 16px; }
.pagination-container { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
