<template>
  <div class="app-container">
    <!-- 可观测统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="4">
        <el-card shadow="hover" class="stats-card">
          <div class="stats-label">总调用次数</div>
          <div class="stats-value">{{ stats.total ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stats-card">
          <div class="stats-label">成功率</div>
          <div class="stats-value" :style="{ color: (stats.successRate ?? 0) >= 0.9 ? '#67c23a' : '#e6a23c' }">
            {{ formatPercent(stats.successRate) }}
          </div>
        </el-card>
      </el-col>
      <el-col :span="5">
        <el-card shadow="hover" class="stats-card">
          <div class="stats-label">语义缓存命中率</div>
          <div class="stats-value" style="color: #409eff">{{ formatPercent(stats.cacheHitRate) }}</div>
          <div class="stats-sub">命中 {{ stats.cacheHit ?? 0 }} / 共 {{ stats.total ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="5">
        <el-card shadow="hover" class="stats-card">
          <div class="stats-label">Token 总消耗</div>
          <div class="stats-value">{{ formatNumber(stats.totalTokens) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stats-card">
          <div class="stats-label">平均耗时</div>
          <div class="stats-value">{{ stats.avgElapsedMs != null ? stats.avgElapsedMs.toFixed(0) : '-' }} ms</div>
        </el-card>
      </el-col>
    </el-row>

    <div class="filter-container">
      <el-input
        v-model="query.providerCode"
        placeholder="供应商编码"
        style="width: 150px"
        clearable
        @keyup.enter="handleSearch"
      />
      <el-input
        v-model="query.modelName"
        placeholder="模型名称"
        style="width: 160px; margin-left: 10px"
        clearable
        @keyup.enter="handleSearch"
      />
      <el-input
        v-model="query.userId"
        placeholder="用户ID"
        style="width: 150px; margin-left: 10px"
        clearable
        @keyup.enter="handleSearch"
      />
      <el-select
        v-model="query.success"
        placeholder="调用结果"
        clearable
        style="width: 130px; margin-left: 10px"
      >
        <el-option label="成功" :value="true" />
        <el-option label="失败" :value="false" />
      </el-select>
      <el-button type="primary" :icon="Search" style="margin-left: 10px" @click="handleSearch">
        查询
      </el-button>
      <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      <el-button
        v-permission="'system:ai-invoke-log:clear'"
        type="danger"
        :icon="Delete"
        style="margin-left: auto"
        @click="handleClear"
      >
        清空记录
      </el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" align="center" />
      <el-table-column label="用户" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span :title="`用户ID: ${row.userId || '-'}`">{{ row.userName || formatUserName(row.userId) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="providerName" label="供应商" width="140" show-overflow-tooltip />
      <el-table-column prop="modelName" label="模型" width="150" show-overflow-tooltip />
      <el-table-column prop="complexity" label="复杂度" width="90" align="center" />
      <el-table-column label="缓存" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.cached === true" type="success">命中</el-tag>
          <el-tag v-else-if="row.cached === false" type="info">未命中</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="Token" width="150" align="center">
        <template #default="{ row }">
          <span v-if="row.totalTokens != null">{{ row.totalTokens }}</span>
          <span v-else>-</span>
          <el-tooltip v-if="row.promptTokens != null" placement="top">
            <template #content>
              输入 {{ row.promptTokens }} / 输出 {{ row.completionTokens }}
            </template>
            <el-icon style="margin-left: 4px"><InfoFilled /></el-icon>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="工具" width="90" align="center">
        <template #default="{ row }">
          <template v-if="(row.toolCount || 0) > 0 && row.toolNames?.length">
            <el-popover placement="top" :width="220" trigger="click">
              <template #reference>
                <el-tag type="warning" style="cursor: pointer">{{ row.toolCount }}</el-tag>
              </template>
              <div class="tool-popover-title">调用的工具</div>
              <div class="tool-popover-list">
                <el-tag v-for="t in row.toolNames" :key="t" size="small" type="info" effect="plain" style="margin: 0 4px 4px 0">{{ t }}</el-tag>
              </div>
            </el-popover>
          </template>
          <span v-else>{{ row.toolCount || 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="elapsedMs" label="耗时(ms)" width="100" align="center" />
      <el-table-column label="结果" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.success ? 'success' : 'danger'">
            {{ row.success ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="调用时间" width="170">
        <template #default="{ row }">{{ formatTime(row.invokeTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="300" align="center">
        <template #default="{ row }">
          <el-button
            v-permission="'system:ai-invoke-log:query'"
            type="primary"
            link
            :icon="View"
            @click="handleDetail(row)"
          >
            详情
          </el-button>
          <el-button
            v-permission="'system:ai-invoke-log:delete'"
            type="danger"
            link
            :icon="Delete"
            @click="handleDelete(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pagination"
      background
      layout="total, sizes, prev, pager, next"
      :total="total"
      :page-size="query.size"
      :current-page="query.page"
      :page-sizes="[10, 20, 50, 100]"
      @size-change="handleSizeChange"
      @current-change="handlePageChange"
    />

    <el-drawer v-model="drawerVisible" title="调用详情" size="55%" :destroy-on-close="true">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="调用结果">
          <el-tag :type="detail.success ? 'success' : 'danger'">
            {{ detail.success ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.providerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="模型">{{ detail.modelName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ detail.providerType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="复杂度">{{ detail.complexity || '-' }}</el-descriptions-item>
        <el-descriptions-item label="会话ID">{{ detail.sessionId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用户">{{ detail.userName || detail.userId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ detail.userId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Token(总)">{{ detail.totalTokens ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="输入/输出">
          {{ detail.promptTokens ?? '-' }} / {{ detail.completionTokens ?? '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="工具数量">{{ detail.toolCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="耗时(ms)">{{ detail.elapsedMs ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="调用时间" :span="2">{{ formatTime(detail.invokeTime) }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.errorMsg" label="错误信息" :span="2">
          <span style="color: #f56c6c">{{ detail.errorMsg }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">工具名</el-divider>
      <el-tag v-for="t in (detail?.toolNames || [])" :key="t" style="margin: 0 6px 6px 0">{{ t }}</el-tag>
      <span v-if="!detail?.toolNames?.length">无</span>

      <el-divider content-position="left">RAG 引用</el-divider>
      <pre class="raw-block">{{ formatList(detail?.ragReferences) }}</pre>

      <el-divider content-position="left">上下文内容</el-divider>
      <pre class="raw-block">{{ detail?.contextContent || '无' }}</pre>

      <el-divider content-position="left">用户输入</el-divider>
      <pre class="raw-block">{{ detail?.userInput || '无' }}</pre>

      <el-divider content-position="left">AI 输出</el-divider>
      <pre class="raw-block">{{ detail?.aiOutput || '无' }}</pre>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Delete, View, InfoFilled } from '@element-plus/icons-vue'
import {
  getAiInvokeLogs,
  deleteAiInvokeLog,
  clearAiInvokeLogs,
  getAiInvokeLogById,
  getUsers,
  getAiInvokeLogStats,
  type AiInvokeLog,
  type AiInvokeLogQuery,
  type AiInvokeLogStats
} from '@/api/system'
import { formatTime } from '@/utils/format'

const loading = ref(false)
const list = ref<AiInvokeLog[]>([])
const total = ref(0)
const drawerVisible = ref(false)
const detail = ref<AiInvokeLog | null>(null)
const userMap = ref<Map<string | number, string>>(new Map())
const stats = ref<AiInvokeLogStats>({})

const query = reactive<AiInvokeLogQuery>({
  page: 1,
  size: 10,
  providerCode: '',
  modelName: '',
  userId: '',
  success: undefined
})

async function loadData() {
  loading.value = true
  try {
    const res = await getAiInvokeLogs(query)
    if (res.code === 200) {
      list.value = res.data?.records || []
      total.value = res.data?.total || 0
    } else {
      ElMessage.error(res.message || '查询失败')
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadData()
}

function handleReset() {
  query.providerCode = ''
  query.modelName = ''
  query.userId = ''
  query.success = undefined
  query.page = 1
  loadData()
}

function handlePageChange(page: number) {
  query.page = page
  loadData()
}

function handleSizeChange(size: number) {
  query.size = size
  query.page = 1
  loadData()
}

async function handleDetail(row: AiInvokeLog) {
  try {
    const res = await getAiInvokeLogById(row.id)
    if (res.code === 200) {
      detail.value = res.data
      drawerVisible.value = true
    } else {
      ElMessage.error(res.message || '获取详情失败')
    }
  } catch (e) {
    console.error(e)
  }
}

async function handleDelete(row: AiInvokeLog) {
  try {
    await ElMessageBox.confirm(`确认删除调用记录 #${row.id}？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteAiInvokeLog(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    console.error(e)
  }
}

async function handleClear() {
  try {
    await ElMessageBox.confirm('确认清空所有 AI 调用记录？此操作不可恢复！', '警告', { type: 'warning' })
  } catch {
    return
  }
  try {
    await clearAiInvokeLogs()
    ElMessage.success('已清空')
    loadStats()
    loadData()
  } catch (e) {
    console.error(e)
  }
}

function formatList(values?: string[]): string {
  if (!values || values.length === 0) {
    return '无'
  }
  return values.join('\n\n')
}

function formatUserName(userId?: string | number): string {
  if (userId == null) return '-'
  return userMap.value.get(userId) || String(userId)
}

async function loadUserMap() {
  try {
    const res = await getUsers()
    if (res.code === 200 && res.data) {
      const map = new Map<string | number, string>()
      for (const u of res.data) {
        // 优先使用 nickname，其次 username；如果后端后续返回 name 字段，也可优先使用
        const displayName = u.nickname || u.username || String(u.id)
        map.set(u.id, displayName)
      }
      userMap.value = map
    }
  } catch (e) {
    console.error('加载用户列表失败', e)
  }
}

onMounted(() => {
  loadUserMap()
  loadStats()
  loadData()
})

async function loadStats() {
  try {
    const res = await getAiInvokeLogStats()
    if (res.code === 200) {
      stats.value = res.data || {}
    }
  } catch (e) {
    console.error('加载 AI 调用统计失败', e)
  }
}

function formatPercent(value?: number): string {
  if (value == null) return '-'
  return (value * 100).toFixed(1) + '%'
}

function formatNumber(value?: number): string {
  if (value == null) return '-'
  return value.toLocaleString()
}
</script>

<style scoped>
.stats-row {
  margin-bottom: 16px;
}
.stats-card {
  text-align: center;
}
.stats-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}
.stats-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}
.stats-sub {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 4px;
}
.filter-container {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  margin-bottom: 16px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
.raw-block {
  background: #f7f8fa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 10px;
  max-height: 280px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 13px;
  line-height: 1.6;
}
</style>
