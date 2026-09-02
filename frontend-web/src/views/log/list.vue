<template>
  <div class="app-container">
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="filter">
        <el-form-item label="模块">
          <el-select v-model="filter.module" clearable placeholder="全部" style="width: 140px">
            <el-option label="auth-server" value="auth-server" />
            <el-option label="system-server" value="system-server" />
            <el-option label="auth-flow" value="auth-flow" />
            <el-option label="auth-message" value="auth-message" />
            <el-option label="ai-agent-server" value="ai-agent-server" />
            <el-option label="gateway" value="gateway" />
          </el-select>
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="filter.level" clearable placeholder="全部" style="width: 100px">
            <el-option label="DEBUG" value="DEBUG" />
            <el-option label="INFO" value="INFO" />
            <el-option label="WARN" value="WARN" />
            <el-option label="ERROR" value="ERROR" />
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
          />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="filter.keyword" placeholder="消息关键词" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table :data="logs" v-loading="loading" border stripe style="width: 100%">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div style="padding: 12px 24px">
              <p v-if="row.exceptionStack"><strong>异常堆栈:</strong></p>
              <pre v-if="row.exceptionStack" style="white-space: pre-wrap; font-size: 12px; color: #f56c6c; max-height: 300px; overflow-y: auto">{{ row.exceptionStack }}</pre>
              <p v-if="row.traceId"><strong>TraceId:</strong> {{ row.traceId }}</p>
              <p v-if="row.errorFingerprint"><strong>错误指纹:</strong> {{ row.errorFingerprint }}</p>
              <p v-if="row.requestUri"><strong>请求URI:</strong> {{ row.requestUri }}</p>
              <p v-if="row.httpStatus"><strong>HTTP状态:</strong> {{ row.httpStatus }}</p>
              <p v-if="row.costTime !== null"><strong>耗时:</strong> {{ row.costTime }}ms</p>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="180">
          <template #default="{ row }">
            {{ formatLogTime(row.logTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="level" label="级别" width="80">
          <template #default="{ row }">
            <el-tag :type="levelTag(row.level)" size="small">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="module" label="模块" width="130" />
        <el-table-column prop="message" label="消息" min-width="300" show-overflow-tooltip />
        <el-table-column prop="username" label="用户" width="100" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleAnalyze(row)" :disabled="!row.errorFingerprint">AI分析</el-button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { queryLogs, getLogStats } from '@/api/log'
import type { LogQueryRequest } from '@/types'

const router = useRouter()
const loading = ref(false)
const logs = ref<any[]>([])
const total = ref(0)
const filter = reactive({ module: '', level: '', keyword: '', timeRange: [] as string[], page: 1, size: 20 })

function levelTag(level: string) {
  return { DEBUG: 'info', INFO: '', WARN: 'warning', ERROR: 'danger' }[level] as any || 'info'
}

function formatLogTime(time?: string) {
  if (!time) return '-'
  return time.replace('T', ' ')
}

async function loadData() {
  loading.value = true
  try {
    const payload: LogQueryRequest = {
      module: filter.module || undefined,
      level: filter.level || undefined,
      keyword: filter.keyword || undefined,
      page: filter.page,
      size: filter.size
    }
    if (filter.timeRange && filter.timeRange.length === 2) {
      payload.startTime = filter.timeRange[0]
      payload.endTime = filter.timeRange[1]
    }
    const res = await queryLogs(payload)
    const result = res?.data
    logs.value = result?.list || []
    total.value = result?.total || 0
  } catch { logs.value = []; total.value = 0 }
  finally { loading.value = false }
}

function resetFilter() {
  filter.module = ''
  filter.level = ''
  filter.keyword = ''
  filter.timeRange = []
  filter.page = 1
  filter.size = 20
  loadData()
}

function handleAnalyze(row: any) {
  if (row.errorFingerprint) {
    router.push(`/log/analysis?fingerprint=${row.errorFingerprint}`)
  }
}

onMounted(loadData)
</script>

<style scoped>
.filter-card { margin-bottom: 16px; }
</style>
