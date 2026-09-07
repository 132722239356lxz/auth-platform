<template>
  <div class="app-container">
    <el-row :gutter="16">
      <!-- 预警汇总卡片 -->
      <el-col :span="24">
        <el-row :gutter="12">
          <el-col :xs="12" :sm="6" v-for="card in summaryCards" :key="card.label">
            <el-card shadow="hover" class="summary-card">
              <div :class="['summary-icon', card.type]">
                <el-icon :size="24"><component :is="card.icon" /></el-icon>
              </div>
              <div class="summary-info">
                <div class="summary-value">{{ card.value }}</div>
                <div class="summary-label">{{ card.label }}</div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-col>

      <!-- 筛选区 -->
      <el-col :span="24" style="margin-top: 16px">
        <el-card shadow="never" class="filter-card">
          <el-form :inline="true" :model="filter" size="default">
            <el-form-item label="关键词">
              <el-input
                v-model="filter.keyword"
                placeholder="预警名称/内容/建议"
                clearable
                @keyup.enter="handleSearch"
                style="width: 200px"
              />
            </el-form-item>
            <el-form-item label="分析类型">
              <el-select v-model="filter.analysisType" placeholder="全部" clearable style="width: 140px">
                <el-option label="趋势分析" value="TREND" />
                <el-option label="异常检测" value="ANOMALY" />
                <el-option label="阈值预警" value="THRESHOLD" />
                <el-option label="趋势预测" value="PREDICTION" />
                <el-option label="智能洞察" value="INSIGHT" />
              </el-select>
            </el-form-item>
            <el-form-item label="级别">
              <el-select v-model="filter.level" placeholder="全部" clearable style="width: 120px">
                <el-option label="紧急" value="CRITICAL" />
                <el-option label="警告" value="WARN" />
                <el-option label="提示" value="INFO" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="filter.resolved" placeholder="全部" clearable style="width: 120px">
                <el-option label="待处理" :value="false" />
                <el-option label="已处理" :value="true" />
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
      </el-col>

      <!-- 预警列表 -->
      <el-col :span="24" style="margin-top: 16px">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>预警列表</span>
              <el-button size="small" @click="loadData">刷新</el-button>
            </div>
          </template>

          <el-table :data="alerts" v-loading="loading" border stripe>
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="alertName" label="预警名称" min-width="180" show-overflow-tooltip />
            <el-table-column prop="analysisType" label="分析类型" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ analysisTypeText(row.analysisType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="alertLevel" label="级别" width="90">
              <template #default="{ row }">
                <el-tag :type="levelTag(row.alertLevel)" size="small">{{ levelText(row.alertLevel) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.resolved ? 'success' : 'warning'" size="small">
                  {{ row.resolved ? '已处理' : '待处理' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="时间" width="170">
              <template #default="{ row }">
                {{ formatTime(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column prop="suggestion" label="建议" min-width="200" show-overflow-tooltip />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button v-if="!row.resolved" size="small" type="success" @click="handleResolve(row)">处理</el-button>
                <el-button size="small" @click="handleView(row)">详情</el-button>
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
      </el-col>

      <!-- 关键指标 -->
      <el-col :span="24" style="margin-top: 16px">
        <el-card shadow="never">
          <template #header><div class="card-header"><span>关键业务指标</span></div></template>
          <el-empty v-if="!metrics || !Object.keys(metrics).length" description="暂无指标数据" />
          <el-descriptions v-else :column="3" border>
            <el-descriptions-item v-for="(val, key) in metrics" :key="key" :label="String(key)">
              {{ val }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <!-- 预警详情弹窗 -->
    <el-dialog v-model="detailVisible" title="预警详情" width="600px">
      <el-descriptions :column="1" border v-if="currentAlert">
        <el-descriptions-item label="预警名称">{{ currentAlert.alertName }}</el-descriptions-item>
        <el-descriptions-item label="分析类型">{{ analysisTypeText(currentAlert.analysisType) }}</el-descriptions-item>
        <el-descriptions-item label="级别">
          <el-tag :type="levelTag(currentAlert.alertLevel)" size="small">{{ levelText(currentAlert.alertLevel) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="currentAlert.resolved ? 'success' : 'warning'" size="small">
            {{ currentAlert.resolved ? '已处理' : '待处理' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="时间">{{ formatTime(currentAlert.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="数据来源">{{ currentAlert.dataSource || '-' }}</el-descriptions-item>
        <el-descriptions-item label="建议">{{ currentAlert.suggestion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="分析详情">{{ currentAlert.analysisDetail || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getAlerts, getAlertsSummary, getMetrics, resolveAlert } from '@/api/ai'
import { useUserStore } from '@/stores/user'
import type { AlertVO } from '@/types'

const userStore = useUserStore()
const loading = ref(false)
const alerts = ref<AlertVO[]>([])
const total = ref(0)
const metrics = ref<any>(null)
const detailVisible = ref(false)
const currentAlert = ref<AlertVO | null>(null)

const filter = reactive({
  keyword: '',
  analysisType: '',
  level: '',
  resolved: undefined as boolean | undefined,
  timeRange: [] as string[],
  page: 1,
  size: 20
})

const summaryCards = reactive([
  { label: 'CRITICAL', value: 0, type: 'danger', icon: 'WarningFilled' },
  { label: 'WARN', value: 0, type: 'warning', icon: 'Warning' },
  { label: 'INFO', value: 0, type: 'info', icon: 'InfoFilled' },
  { label: '待处理', value: 0, type: 'primary', icon: 'Bell' }
])

function levelText(level?: string) {
  return { CRITICAL: '紧急', WARN: '警告', INFO: '提示' }[level || ''] || level || '-'
}

function levelTag(level?: string) {
  return { CRITICAL: 'danger', WARN: 'warning', INFO: 'info' }[level || ''] as any || 'info'
}

function analysisTypeText(type?: string) {
  const map: Record<string, string> = {
    TREND: '趋势分析',
    ANOMALY: '异常检测',
    THRESHOLD: '阈值预警',
    PREDICTION: '趋势预测',
    INSIGHT: '智能洞察'
  }
  return map[type || ''] || type || '-'
}

function formatTime(time?: string) {
  if (!time) return '-'
  return time.replace('T', ' ').slice(0, 19)
}

async function loadData() {
  loading.value = true
  try {
    const [alertsRes, summaryRes, metricsRes] = await Promise.allSettled([
      getAlerts({
        keyword: filter.keyword || undefined,
        analysisType: filter.analysisType || undefined,
        level: filter.level || undefined,
        ...(filter.resolved !== undefined && { resolved: filter.resolved }),
        startTime: filter.timeRange?.[0] || undefined,
        endTime: filter.timeRange?.[1] || undefined,
        page: filter.page,
        size: filter.size
      }),
      getAlertsSummary(),
      getMetrics()
    ])

    if (alertsRes.status === 'fulfilled') {
      const data = alertsRes.value?.data
      alerts.value = data?.list || []
      total.value = data?.total || 0
    }
    if (summaryRes.status === 'fulfilled' && summaryRes.value?.data) {
      const s = summaryRes.value.data
      summaryCards[0].value = s.critical || 0
      summaryCards[1].value = s.warn || 0
      summaryCards[2].value = s.info || 0
      summaryCards[3].value = s.pending || 0
    }
    if (metricsRes.status === 'fulfilled') {
      metrics.value = metricsRes.value?.data
    }
  } catch {
    alerts.value = []
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
  filter.analysisType = ''
  filter.level = ''
  filter.resolved = undefined
  filter.timeRange = []
  filter.page = 1
  filter.size = 20
  loadData()
}

function handleView(row: AlertVO) {
  currentAlert.value = row
  detailVisible.value = true
}

async function handleResolve(row: AlertVO) {
  try {
    await resolveAlert(row.id, userStore.username)
    ElMessage.success('预警已处理')
    loadData()
  } catch { /* */ }
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }

.filter-card { margin-bottom: 0; }

.summary-card {
  .el-card__body { display: flex; align-items: center; gap: 12px; padding: 16px; }
}

.summary-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 12px;
}

.summary-icon {
  width: 48px; height: 48px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  color: #fff; flex-shrink: 0;
  &.danger { background: #f56c6c; }
  &.warning { background: #e6a23c; }
  &.info { background: #909399; }
  &.primary { background: #409eff; }
}

.summary-value { font-size: 24px; font-weight: 700; color: #303133; }
.summary-label { font-size: 12px; color: #909399; margin-top: 2px; }
.pagination-container { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
