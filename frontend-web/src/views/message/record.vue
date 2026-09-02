<template>
  <div class="app-container">
    <!-- 筛选区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="filter" size="default">
        <el-form-item label="关键词">
          <el-input v-model="filter.keyword" placeholder="标题/内容搜索" clearable @keyup.enter="handleSearch" style="width: 200px" />
        </el-form-item>
        <el-form-item label="消息类型">
          <el-select v-model="filter.messageType" placeholder="全部" clearable style="width: 140px">
            <el-option label="系统通知" value="SYSTEM_NOTICE" />
            <el-option label="事件推送" value="EVENT_PUSH" />
            <el-option label="审批通知" value="APPROVAL_NOTIFY" />
            <el-option label="子系统通信" value="SUBSYSTEM_COMM" />
            <el-option label="用户消息" value="USER_MESSAGE" />
          </el-select>
        </el-form-item>
        <el-form-item label="渠道">
          <el-select v-model="filter.channels" placeholder="全部" clearable style="width: 140px">
            <el-option
              v-for="opt in channelOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filter.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="发送中" value="SENDING" />
            <el-option label="已发送" value="SENT" />
            <el-option label="待处理" value="PENDING" />
            <el-option label="失败" value="FAILED" />
            <el-option label="部分失败" value="PARTIAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="发送人">
          <el-input v-model="filter.sender" placeholder="发送人" clearable @keyup.enter="handleSearch" style="width: 150px" />
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

    <!-- 数据表格 -->
    <el-card shadow="never">
      <el-table :data="records" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="messageId" label="消息ID" width="180" show-overflow-tooltip />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="messageType" label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.messageType)">{{ typeText(row.messageType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="渠道" width="140">
          <template #default="{ row }">
            <el-tag
              v-for="c in splitChannels(row.channels)"
              :key="c"
              :type="channelType(c)"
              size="small"
              style="margin: 2px"
            >{{ channelLabel(c) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sender" label="发送人" width="120" show-overflow-tooltip />
        <el-table-column prop="receivers" label="接收人" width="140" show-overflow-tooltip />
        <el-table-column label="发送时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.sendTime || row.createTime) }}
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
    <el-dialog v-model="detailVisible" title="发送记录详情" width="650px">
      <el-descriptions v-if="currentRecord" :column="2" border>
        <el-descriptions-item label="消息ID" :span="2">{{ currentRecord.messageId }}</el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ currentRecord.title }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ typeText(currentRecord.messageType) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(currentRecord.status)">{{ statusText(currentRecord.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="渠道" :span="2">
          <el-tag
            v-for="c in splitChannels(currentRecord.channels)"
            :key="c"
            :type="channelType(c)"
            size="small"
            style="margin: 2px"
          >{{ channelLabel(c) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发送人">{{ currentRecord.sender || '-' }}</el-descriptions-item>
        <el-descriptions-item label="接收人">{{ currentRecord.receivers || '-' }}</el-descriptions-item>
        <el-descriptions-item label="目标子系统">{{ currentRecord.targetSubsystems || '-' }}</el-descriptions-item>
        <el-descriptions-item label="业务ID">{{ currentRecord.businessId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="模板编码">{{ currentRecord.templateCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="失败原因" :span="2">{{ currentRecord.failReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(currentRecord.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="发送时间">{{ formatTime(currentRecord.sendTime) }}</el-descriptions-item>
        <el-descriptions-item label="内容" :span="2">
          <div style="white-space: pre-wrap; max-height: 200px; overflow-y: auto">{{ currentRecord.content }}</div>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { queryMessages } from '@/api/message'
import type { MessageInfo } from '@/types'
import { channelOptions, channelLabel, channelType } from '@/utils/channel'
import { formatTime } from '@/utils/format'

const loading = ref(false)
const records = ref<MessageInfo[]>([])
const total = ref(0)
const detailVisible = ref(false)
const currentRecord = ref<MessageInfo | null>(null)

const filter = reactive({
  keyword: '', messageType: '', channels: '', status: '', sender: '',
  timeRange: [] as string[], page: 1, size: 20
})

function typeText(type?: string) {
  const map: Record<string, string> = {
    SYSTEM_NOTICE: '系统通知', EVENT_PUSH: '事件推送', APPROVAL_NOTIFY: '审批通知',
    SUBSYSTEM_COMM: '子系统通信', USER_MESSAGE: '用户消息'
  }
  return map[type || ''] || type || '-'
}

function typeTag(type?: string) {
  const map: Record<string, string> = {
    SYSTEM_NOTICE: '', EVENT_PUSH: 'warning', APPROVAL_NOTIFY: 'success',
    SUBSYSTEM_COMM: 'info', USER_MESSAGE: ''
  }
  return map[type || ''] || undefined
}

function statusText(s?: string) {
  return { PENDING: '待处理', SENDING: '发送中', SENT: '已发送', PARTIAL: '部分失败', FAILED: '失败' }[s || ''] || s || '-'
}

function statusTag(s?: string) {
  return { PENDING: 'warning', SENDING: '', SENT: 'success', PARTIAL: 'warning', FAILED: 'danger' }[s || ''] as any || 'info'
}

function splitChannels(channels?: string) {
  return channels ? channels.split(',').map(c => c.trim()).filter(Boolean) : []
}

async function loadData() {
  loading.value = true
  try {
    const res = await queryMessages({
      keyword: filter.keyword || undefined,
      messageType: filter.messageType || undefined,
      channels: filter.channels || undefined,
      status: filter.status || undefined,
      sender: filter.sender || undefined,
      startTime: filter.timeRange?.[0] || undefined,
      endTime: filter.timeRange?.[1] || undefined,
      page: filter.page,
      size: filter.size
    })
    const data = res?.data
    records.value = data?.list || []
    total.value = data?.total || 0
  } catch {
    records.value = []
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
  filter.messageType = ''
  filter.channels = ''
  filter.status = ''
  filter.sender = ''
  filter.timeRange = []
  filter.page = 1
  filter.size = 20
  loadData()
}

function handleView(row: MessageInfo) {
  currentRecord.value = row
  detailVisible.value = true
}

onMounted(loadData)
</script>

<style scoped>
.filter-card { margin-bottom: 16px; }
.pagination-container { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
