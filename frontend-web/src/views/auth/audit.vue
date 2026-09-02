<template>
  <div class="app-container">
    <el-tabs v-model="activeTab">
      <!-- 概览统计 -->
      <el-tab-pane label="概览统计" name="dashboard">
        <el-row :gutter="16">
          <el-col :xs="12" :sm="6" v-for="card in dashboardCards" :key="card.label">
            <el-card shadow="hover">
              <div class="stat-card">
                <el-statistic :title="card.label" :value="card.value" />
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- 吊销日志 -->
      <el-tab-pane label="吊销日志" name="revokeLogs">
        <el-card shadow="never">
          <div class="filter-container" style="margin-bottom: 12px">
            <el-input v-model="revokeFilter.clientId" placeholder="客户端ID" style="width: 160px" clearable @keyup.enter="loadRevokeLogs" />
            <el-input v-model="revokeFilter.username" placeholder="用户名" style="width: 140px" clearable @keyup.enter="loadRevokeLogs" />
            <el-select v-model="revokeFilter.revokeType" placeholder="吊销类型" clearable style="width: 130px">
              <el-option label="用户登出" :value="1" />
              <el-option label="强制下线" :value="2" />
              <el-option label="凭证失效" :value="3" />
            </el-select>
            <el-button type="primary" @click="loadRevokeLogs">查询</el-button>
            <el-button @click="handleResetRevoke">重置</el-button>
          </div>
          <el-table :data="revokeLogList" v-loading="revokeLoading" border stripe>
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="userId" label="用户名称" width="100" show-overflow-tooltip />
            <el-table-column prop="clientId" label="客户端ID" min-width="130" show-overflow-tooltip />
            <el-table-column prop="clientName" label="客户端名称" min-width="130" show-overflow-tooltip />
            <el-table-column prop="tokenType" label="Token类型" width="110" />
            <el-table-column prop="tokenSnip" label="Token片段" min-width="120" show-overflow-tooltip />
            <el-table-column label="吊销类型" width="110">
              <template #default="{ row }">
                <el-tag :type="revokeTypeTag(row.revokeType)" size="small">
                  {{ row.revokeTypeDesc || revokeTypeText(row.revokeType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ row.remark || '-' }}</template>
            </el-table-column>
            <el-table-column label="吊销时间" width="170">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="revokePagination.page"
            v-model:page-size="revokePagination.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="revokePagination.total"
            layout="total, sizes, prev, pager, next, jumper"
            background
            class="pagination-container"
            @size-change="loadRevokeLogs"
            @current-change="loadRevokeLogs"
          />
        </el-card>
      </el-tab-pane>

      <!-- 强制吊销 -->
      <el-tab-pane label="强制吊销" name="revoke">
        <el-card shadow="never" style="max-width: 600px; margin: 0 auto">
          <el-form :model="revokeForm" :rules="revokeFormRules" ref="revokeFormRef" label-width="100px">
            <el-form-item label="用户名称" prop="userId">
              <el-input v-model="revokeForm.userId" placeholder="请输入用户名称Id" />
            </el-form-item>
            <el-form-item label="客户端ID" prop="clientId">
              <el-input v-model="revokeForm.clientId" placeholder="请输入客户端主键ID" />
            </el-form-item>
            <el-form-item label="Token类型">
              <el-select v-model="revokeForm.tokenType" style="width: 100%">
                <el-option label="全部" value="ALL" />
                <el-option label="Access Token" value="ACCESS_TOKEN" />
                <el-option label="Refresh Token" value="REFRESH_TOKEN" />
              </el-select>
            </el-form-item>
            <el-form-item label="吊销类型" prop="revokeType">
              <el-select v-model="revokeForm.revokeType" style="width: 100%">
                <el-option label="1 - 用户主动登出" :value="1" />
                <el-option label="2 - 后台强制下线" :value="2" />
                <el-option label="3 - IAM凭证失效" :value="3" />
              </el-select>
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="revokeForm.remark" type="textarea" :rows="2" placeholder="吊销原因备注（可选）" />
            </el-form-item>
            <el-form-item>
              <el-button type="danger" @click="handleRevoke">执行吊销</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getAuditDashboard, getRevokeLogsPage, revokeToken } from '@/api/auth'
import { formatTime } from '@/utils/format'
import type { RevokeLogResponse } from '@/types'

const activeTab = ref('dashboard')
const revokeLoading = ref(false)
const revokeLogList = ref<RevokeLogResponse[]>([])
const revokeFilter = reactive({ clientId: '', username: '', revokeType: undefined as number | undefined })
const revokePagination = reactive({ page: 1, pageSize: 10, total: 0 })

const dashboardCards = ref([
  { label: '总客户端数', value: 0 },
  { label: '总授权记录', value: 0 },
  { label: '吊销记录', value: 0 },
])

const revokeFormRef = ref<FormInstance>()
const revokeForm = reactive({
  userId: '',
  clientId: '',
  tokenType: 'ALL',
  revokeType: 2 as number,
  remark: '',
})

const revokeFormRules: FormRules = {
  userId: [{ required: true, message: '请输入用户名称', trigger: 'blur' }],
  clientId: [{ required: true, message: '请输入客户端ID', trigger: 'blur' }],
  revokeType: [{ required: true, message: '请选择吊销类型', trigger: 'change' }],
}

function revokeTypeText(type: number) {
  return { 1: '登出', 2: '强制下线', 3: '凭证失效' }[type] || '未知'
}

function revokeTypeTag(type: number) {
  return { 1: 'info', 2: 'warning', 3: 'danger' }[type] || 'info'
}

async function loadDashboard() {
  try {
    const res = await getAuditDashboard()
    if (res?.data) {
      dashboardCards.value[0].value = res.data.totalClients || 0
      dashboardCards.value[1].value = res.data.totalAuthorizations || 0
      dashboardCards.value[2].value = res.data.totalRevokeLogs || 0
    }
  } catch { /* handled by interceptor */ }
}

async function loadRevokeLogs() {
  revokeLoading.value = true
  try {
    const res = await getRevokeLogsPage({
      clientId: revokeFilter.clientId || undefined,
      username: revokeFilter.username || undefined,
      revokeType: revokeFilter.revokeType,
      page: revokePagination.page,
      pageSize: revokePagination.pageSize,
    })
    const data = res?.data
    revokeLogList.value = data?.records || []
    revokePagination.total = data?.total || 0
  } catch { revokeLogList.value = [] }
  finally { revokeLoading.value = false }
}

function handleResetRevoke() {
  revokeFilter.clientId = ''
  revokeFilter.username = ''
  revokeFilter.revokeType = undefined
  revokePagination.page = 1
  loadRevokeLogs()
}

async function handleRevoke() {
  const valid = await revokeFormRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    await ElMessageBox.confirm('确定要吊销此Token吗？此操作不可逆。', '危险操作', { type: 'warning' })
    // 后端 RevokeLogRequest 字段: userId, clientId, tokenType, revokeType, remark
    // userId 字段实际接收用户名称（用户名）
    await revokeToken({
      userId: revokeForm.userId,
      clientId: revokeForm.clientId,
      tokenType: revokeForm.tokenType,
      revokeType: revokeForm.revokeType,
      remark: revokeForm.remark || undefined,
    })
    ElMessage.success('Token吊销成功')
    loadRevokeLogs()
  } catch { /* cancelled */ }
}

onMounted(() => {
  loadDashboard()
  loadRevokeLogs()
})
</script>

<style scoped>
.stat-card { text-align: center; padding: 20px 0; }
.filter-container { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
</style>
