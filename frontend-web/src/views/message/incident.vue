<template>
  <div class="app-container">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" style="color: #909399">{{ stats.total }}</div>
          <div class="stat-label">异常总数</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" style="color: #e6a23c">{{ stats.pending }}</div>
          <div class="stat-label">未处理</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" style="color: #f56c6c">{{ stats.criticalPending }}</div>
          <div class="stat-label">紧急未处理</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 筛选区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="filter" size="default">
        <el-form-item label="关键词">
          <el-input v-model="filter.keyword" placeholder="标题搜索" clearable @keyup.enter="handleSearch" style="width: 200px" />
        </el-form-item>
        <el-form-item label="子系统">
          <el-input v-model="filter.subsystem" placeholder="子系统标识" clearable @keyup.enter="handleSearch" style="width: 180px" />
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="filter.level" placeholder="全部" clearable style="width: 120px">
            <el-option label="错误" value="ERROR" />
            <el-option label="警告" value="WARN" />
            <el-option label="严重" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filter.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="待处理" value="PENDING" />
            <el-option label="已处理" value="RESOLVED" />
            <el-option label="已忽略" value="IGNORED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 异常列表 -->
    <el-card shadow="never">
      <el-table :data="incidents" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="subsystem" label="子系统" width="120">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ row.subsystemName || row.subsystem }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="level" label="级别" width="80">
          <template #default="{ row }">
            <el-tag :type="levelTag(row.level)" size="small">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="incidentType" label="类型" width="80">
          <template #default="{ row }">
            {{ row.incidentType || 'ERROR' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="incidentStatusTag(row.status)" size="small">{{ incidentStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上报时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.reportedAt || row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleView(row)">查看</el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              size="small" type="success"
              @click="handleResolve(row)">处理</el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              size="small" type="warning"
              @click="handleIgnore(row)">忽略</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
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
    <el-dialog v-model="detailVisible" title="异常详情" width="700px">
      <el-descriptions v-if="currentIncident" :column="2" border>
        <el-descriptions-item label="ID">{{ currentIncident.id }}</el-descriptions-item>
        <el-descriptions-item label="子系统">{{ currentIncident.subsystemName || currentIncident.subsystem }}</el-descriptions-item>
        <el-descriptions-item label="级别">
          <el-tag :type="levelTag(currentIncident.level)">{{ currentIncident.level }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="类型">{{ currentIncident.incidentType || 'ERROR' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="incidentStatusTag(currentIncident.status)">{{ incidentStatusText(currentIncident.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="Trace ID">{{ currentIncident.traceId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ currentIncident.title }}</el-descriptions-item>
        <el-descriptions-item label="上报时间">{{ formatTime(currentIncident.reportedAt) }}</el-descriptions-item>
        <el-descriptions-item label="处理时间">{{ formatTime(currentIncident.resolvedAt) }}</el-descriptions-item>
        <el-descriptions-item label="处理人">{{ currentIncident.resolver || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处理说明">{{ currentIncident.resolveNote || '-' }}</el-descriptions-item>
        <el-descriptions-item label="异常描述" :span="2">
          <div style="white-space: pre-wrap; max-height: 150px; overflow-y: auto">{{ currentIncident.content || '-' }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="异常堆栈" :span="2" v-if="currentIncident.stackTrace">
          <div style="white-space: pre-wrap; max-height: 200px; overflow-y: auto; font-size: 12px; background: #f5f5f5; padding: 8px">
            {{ currentIncident.stackTrace }}
          </div>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { queryIncidents, getIncidentStats, resolveIncident, ignoreIncident, deleteIncident } from '@/api/message'
import type { SubsystemIncident, SubsystemIncidentStats } from '@/types'

const loading = ref(false)
const incidents = ref<SubsystemIncident[]>([])
const total = ref(0)
const stats = ref<SubsystemIncidentStats>({ total: 0, pending: 0, criticalPending: 0 })
const detailVisible = ref(false)
const currentIncident = ref<SubsystemIncident | null>(null)

const filter = reactive({
  keyword: '', subsystem: '', level: '', status: '', page: 1, size: 20
})

function levelTag(level?: string) {
  return { ERROR: 'danger', WARN: 'warning', CRITICAL: 'danger' }[level || ''] as any || 'info'
}

function incidentStatusText(s?: string) {
  return { PENDING: '待处理', RESOLVED: '已处理', IGNORED: '已忽略' }[s || ''] || s || '-'
}

function incidentStatusTag(s?: string) {
  return { PENDING: 'danger', RESOLVED: 'success', IGNORED: 'info' }[s || ''] as any || 'info'
}

function formatTime(time?: string) {
  if (!time) return '-'
  return time.replace('T', ' ').slice(0, 19)
}

async function loadStats() {
  try {
    const res = await getIncidentStats()
    if (res?.data) stats.value = res.data
  } catch { /* */ }
}

async function loadData() {
  loading.value = true
  try {
    const [dataRes] = await Promise.all([queryIncidents({
      keyword: filter.keyword || undefined,
      subsystem: filter.subsystem || undefined,
      level: filter.level || undefined,
      status: filter.status || undefined,
      page: filter.page,
      size: filter.size
    }), loadStats()])
    const data = dataRes?.data
    incidents.value = data?.list || []
    total.value = data?.total || 0
  } catch { incidents.value = []; total.value = 0 }
  finally { loading.value = false }
}

function resetFilter() {
  filter.keyword = ''
  filter.subsystem = ''
  filter.level = ''
  filter.status = ''
  filter.page = 1
  filter.size = 20
  loadData()
}

function handleSearch() {
  filter.page = 1
  loadData()
}

function handleView(row: SubsystemIncident) {
  currentIncident.value = row
  detailVisible.value = true
}

async function handleResolve(row: SubsystemIncident) {
  try {
    await ElMessageBox.prompt('请输入处理说明', '标记已处理', {
      confirmButtonText: '确定', cancelButtonText: '取消',
      inputPlaceholder: '可选：处理说明'
    }).then(async ({ value }) => {
      if (row.id) {
        await resolveIncident(row.id, 'admin', value || undefined)
        ElMessage.success('已标记为已处理')
        loadData()
      }
    })
  } catch { /* */ }
}

async function handleIgnore(row: SubsystemIncident) {
  try {
    await ElMessageBox.prompt('请输入忽略原因', '忽略异常', {
      confirmButtonText: '确定', cancelButtonText: '取消',
      inputPlaceholder: '可选：忽略原因'
    }).then(async ({ value }) => {
      if (row.id) {
        await ignoreIncident(row.id, 'admin', value || undefined)
        ElMessage.success('已忽略')
        loadData()
      }
    })
  } catch { /* */ }
}

async function handleDelete(row: SubsystemIncident) {
  try {
    await ElMessageBox.confirm(`确定删除异常记录 "${row.title}" 吗？`, '提示', { type: 'warning' })
    if (row.id) {
      await deleteIncident(row.id)
      ElMessage.success('删除成功')
      loadData()
    }
  } catch { /* */ }
}

onMounted(async () => {
  await loadStats()
  loadData()
})
</script>

<style scoped>
.stats-row { margin-bottom: 16px; }
.stat-card { text-align: center; cursor: pointer; }
.stat-value { font-size: 36px; font-weight: 700; line-height: 1.2; }
.stat-label { font-size: 14px; color: #909399; margin-top: 4px; }
.filter-card { margin-bottom: 16px; }
.pagination-container { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
