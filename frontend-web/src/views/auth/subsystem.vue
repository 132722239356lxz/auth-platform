<template>
  <div class="app-container">
    <el-card shadow="never" class="filter-card">
      <div class="filter-container">
        <el-input v-model="filter.clientId" placeholder="客户端ID" style="width: 180px" clearable @keyup.enter="handleSearch" />
        <el-input v-model="filter.username" placeholder="用户名" style="width: 140px" clearable @keyup.enter="handleSearch" />
        <el-select v-model="filter.status" placeholder="状态" clearable style="width: 120px">
          <el-option label="有效" value="ACTIVE" />
          <el-option label="已过期" value="EXPIRED" />
          <el-option label="已吊销" value="REVOKED" />
          <el-option label="已刷新" value="REFRESHED" />
        </el-select>
        <el-select v-model="filter.expired" placeholder="过期" clearable style="width: 110px">
          <el-option label="已过期" :value="true" />
          <el-option label="未过期" :value="false" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table :data="tokenList" v-loading="loading" border stripe size="small">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="clientId" label="客户端ID" min-width="130" show-overflow-tooltip />
        <el-table-column prop="username" label="用户名" width="100" />
        <el-table-column prop="accessTokenSnip" label="Access Token" min-width="180" show-overflow-tooltip />
        <el-table-column prop="tokenType" label="Token类型" width="110" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="过期" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.expired ? 'danger' : 'success'" size="small">{{ row.expired ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Access过期时间" width="170">
          <template #default="{ row }">{{ formatTime(row.accessTokenExpiresAt) }}</template>
        </el-table-column>
        <el-table-column label="Refresh过期时间" width="170">
          <template #default="{ row }">{{ formatTime(row.refreshTokenExpiresAt) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="最后刷新" width="170">
          <template #default="{ row }">{{ formatTime(row.lastRefreshTime) }}</template>
        </el-table-column>
        <el-table-column prop="refreshCount" label="刷新次数" width="80" align="center" />
        <el-table-column prop="issuedIp" label="签发IP" width="130" />
        <el-table-column label="吊销时间" width="170">
          <template #default="{ row }">{{ formatTime(row.revokeTime) }}</template>
        </el-table-column>
        <el-table-column prop="revokeReasonDesc" label="吊销原因" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.revokeReasonDesc || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              size="small"
              type="danger"
              :disabled="row.status === 'REVOKED'"
              @click="handleRevoke(row)"
            >吊销</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        class="pagination-container"
        @size-change="handleSearch"
        @current-change="handleSearch"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSubsystemTokensPage, revokeSubsystemToken } from '@/api/auth'
import { formatTime } from '@/utils/format'
import type { SubsystemTokenResponse } from '@/types'

const loading = ref(false)
const tokenList = ref<SubsystemTokenResponse[]>([])
const filter = reactive({ clientId: '', username: '', status: '', expired: undefined as boolean | undefined })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })

const STATUS_MAP: Record<string, { label: string; type: string }> = {
  ACTIVE:    { label: '有效',   type: 'success' },
  EXPIRED:   { label: '已过期', type: 'info' },
  REVOKED:   { label: '已吊销', type: 'danger' },
  REFRESHED: { label: '已刷新', type: 'warning' },
}

function statusTag(status: string) {
  return STATUS_MAP[status]?.type || 'info'
}

function statusLabel(status: string) {
  return STATUS_MAP[status]?.label || status
}

async function loadData() {
  loading.value = true
  try {
    const res = await getSubsystemTokensPage({
      clientId: filter.clientId || undefined,
      username: filter.username || undefined,
      status: filter.status || undefined,
      expired: filter.expired,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    const data = res?.data
    tokenList.value = data?.records || []
    pagination.total = data?.total || 0
  } catch { tokenList.value = [] }
  finally { loading.value = false }
}

function handleSearch() {
  pagination.page = 1
  loadData()
}

function handleReset() {
  filter.clientId = ''
  filter.username = ''
  filter.status = ''
  filter.expired = undefined
  pagination.page = 1
  loadData()
}

async function handleRevoke(row: SubsystemTokenResponse) {
  try {
    await ElMessageBox.confirm(`确定吊销 ${row.username} 的 Token 吗？`, '提示', { type: 'warning' })
    await revokeSubsystemToken(row.id, 2, '管理员手动吊销')
    ElMessage.success('Token 已吊销')
    loadData()
  } catch { /* user cancelled */ }
}

onMounted(loadData)
</script>

<style scoped>
.filter-card { margin-bottom: 16px; }
.filter-container { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
</style>
