<template>
  <div class="portal-page">
    <div class="page-header">
      <el-page-header title="返回" content="我的申请记录" @back="goBack" />
    </div>

    <el-row :gutter="20">
      <el-col :xs="24" :md="8">
        <el-card shadow="hover" class="profile-card">
          <div class="avatar-section">
            <el-avatar :size="80" :src="avatarUrl">
              <el-icon :size="36"><UserFilled /></el-icon>
            </el-avatar>
            <h3>{{ userStore.nickname }}</h3>
            <el-tag :type="userStore.isAdmin ? 'danger' : 'success'" effect="light">
              {{ userStore.isAdmin ? '管理员' : '普通用户' }}
            </el-tag>
          </div>
          <el-divider />
          <div class="nav-links">
            <div class="nav-item" @click="goApply">
              <el-icon><DocumentChecked /></el-icon>
              <span>发起审批申请</span>
            </div>
            <div class="nav-item active" @click="stay">
              <el-icon><Document /></el-icon>
              <span>我的申请记录</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="16">
        <el-card shadow="hover" class="records-card">
          <template #header>
            <div class="card-header">
              <span>申请记录</span>
              <el-button type="primary" @click="goApply">
                <el-icon><Plus /></el-icon> 发起新申请
              </el-button>
            </div>
          </template>

          <el-table
            v-loading="loading"
            :data="list"
            stripe
            empty-text="暂无申请记录"
            class="records-table"
          >
            <el-table-column prop="title" label="申请标题" min-width="180" />
            <el-table-column prop="definitionName" label="类型" width="140">
              <template #default="{ row }">
                <el-tag v-if="row.definitionName" type="info" effect="plain" size="small">
                  {{ row.definitionName }}
                </el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" effect="light" round size="small">
                  {{ statusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="currentNodeName" label="当前节点" width="140" />
            <el-table-column prop="createTime" label="提交时间" width="170">
              <template #default="{ row }">
                {{ formatDate(row.createTime) }}
              </template>
            </el-table-column>
            <el-table-column label="详情" width="90" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  link
                  :icon="row._expand ? ArrowUp : ArrowDown"
                  @click="toggleDetail(row)"
                >
                  {{ row._expand ? '收起' : '查看' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <transition-group name="fade">
            <div
              v-for="row in expandedRows"
              :key="'d' + row.id"
              class="apply-detail"
            >
              <div class="detail-header">
                <span class="detail-title">{{ row.title }}</span>
                <el-tag :type="statusType(row.status)" effect="light" round size="small">
                  {{ statusText(row.status) }}
                </el-tag>
              </div>

              <div class="detail-body">
                <div class="detail-section">
                  <div class="section-title">申请内容</div>
                  <div class="content-grid">
                    <div
                      v-for="item in renderContent(row.applyContent)"
                      :key="item.key"
                      class="content-item"
                    >
                      <div class="content-label">{{ item.label }}</div>
                      <div class="content-value">{{ item.value }}</div>
                    </div>
                  </div>
                </div>

                <el-divider />

                <div class="detail-section">
                  <div class="section-title">审批流程</div>
                  <el-timeline v-if="!detailLoading[row.id] && detailTasks[row.id]?.length">
                    <el-timeline-item
                      v-for="task in detailTasks[row.id]"
                      :key="task.id"
                      :type="taskType(task.status)"
                      :icon="taskIcon(task.status)"
                      :timestamp="formatDate(task.approveTime || task.createTime)"
                    >
                      <div class="timeline-node">{{ task.nodeName }}</div>
                      <div class="timeline-approver">
                        <span class="label">审批人：</span>
                        <span class="value">{{ task.approver || '-' }}</span>
                      </div>
                      <div class="timeline-status">
                        <el-tag :type="taskType(task.status)" effect="plain" size="small">
                          {{ taskStatusText(task.status) }}
                        </el-tag>
                      </div>
                      <div v-if="task.comment" class="timeline-comment">
                        {{ task.comment }}
                      </div>
                    </el-timeline-item>
                  </el-timeline>
                  <el-empty v-else-if="!detailLoading[row.id]" description="暂无审批记录" />
                  <div v-else class="detail-loading">
                    <el-icon class="is-loading"><Loading /></el-icon>
                    加载审批流程中...
                  </div>
                </div>
                </div>
                </div>
                </transition-group>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  UserFilled,
  DocumentChecked,
  Document,
  Plus,
  ArrowDown,
  ArrowUp,
  Loading,
  Select,
  CloseBold,
  Clock
} from '@element-plus/icons-vue'
import { getMyApplications, getWorkflowDetail } from '@/api/workflow'
import { useUserStore } from '@/stores/user'
import type { WorkflowInstance, WorkflowTask } from '@/types'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const list = ref<(WorkflowInstance & { _expand?: boolean })[]>([])
const detailTasks = reactive<Record<number, WorkflowTask[]>>({})
const detailLoading = reactive<Record<number, boolean>>({})

function taskType(status?: string): 'success' | 'warning' | 'info' | 'danger' {
  if (!status) return 'info'
  if (status === 'APPROVED' || status === 'COMPLETED') return 'success'
  if (status === 'REJECTED') return 'danger'
  if (status === 'PENDING' || status === 'IN_PROGRESS') return 'warning'
  return 'info'
}

function taskStatusText(status?: string): string {
  if (!status) return '未知'
  return (
    {
      PENDING: '待审批',
      IN_PROGRESS: '进行中',
      APPROVED: '已通过',
      COMPLETED: '已完成',
      REJECTED: '已驳回',
      TRANSFERRED: '已转交'
    } as Record<string, string>
  )[status] || status
}

function taskIcon(status?: string) {
  if (!status) return Clock
  if (status === 'APPROVED' || status === 'COMPLETED') return Select
  if (status === 'REJECTED') return CloseBold
  return Clock
}

const avatarUrl = computed(() => userStore.userInfo?.avatar || '')
const expandedRows = computed(() => list.value.filter((it) => it._expand))

function statusType(status: string): 'success' | 'warning' | 'info' | 'danger' {
  if (status === 'COMPLETED' || status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  if (status === 'IN_PROGRESS' || status === 'PENDING') return 'warning'
  return 'info'
}

function statusText(status: string): string {
  return (
    {
      COMPLETED: '已完成',
      APPROVED: '已通过',
      REJECTED: '已驳回',
      IN_PROGRESS: '审批中',
      PENDING: '待审批'
    } as Record<string, string>
  )[status] || status
}

function formatDate(date?: string): string {
  if (!date) return ''
  return date.replace('T', ' ')
}

function leaveTypeText(value?: string | number): string {
  const map: Record<string, string> = {
    '0': '年假',
    '1': '事假',
    '2': '病假',
    '3': '调休'
  }
  return map[String(value)] || String(value)
}

function renderContent(content?: string): { key: string; label: string; value: string }[] {
  if (!content) return []
  let obj: Record<string, any>
  try {
    obj = JSON.parse(content)
  } catch {
    return [{ key: 'raw', label: '内容', value: content }]
  }
  const keyMap: Record<string, string> = {
    请假类型: '请假类型',
    请假理由: '请假理由',
    请假开始日期: '开始日期',
    请假结束日期: '结束日期',
    申请理由: '申请理由',
    申请日期: '申请日期'
  }
  const hiddenKeys = ['type', 'targetUserId', 'targetUsername']
  return Object.entries(obj)
    .filter(([k]) => !hiddenKeys.includes(k))
    .map(([k, v]) => {
      const label = keyMap[k] || k
      let value = v
      if (k === '请假类型') value = leaveTypeText(value)
      return { key: k, label, value: String(value ?? '-') }
    })
}

async function toggleDetail(row: WorkflowInstance & { _expand?: boolean }) {
  row._expand = !row._expand
  if (row._expand && !detailTasks[row.id]) {
    detailLoading[row.id] = true
    try {
      const res = await getWorkflowDetail(row.id)
      detailTasks[row.id] = res.data?.tasks || []
    } catch (e: any) {
      ElMessage.error(e?.message || '加载审批详情失败')
      detailTasks[row.id] = []
    } finally {
      detailLoading[row.id] = false
    }
  }
}

function stay() {
  // 当前页
}

function goBack() {
  router.back()
}

function goApply() {
  router.push('/portal/apply')
}

async function load() {
  loading.value = true
  try {
    const u = userStore.userInfo
    const res = await getMyApplications(u?.username || '')
    list.value = (res.data || []).map((it: WorkflowInstance) => ({ ...it, _expand: false }))
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped lang="scss">
.portal-page {
  max-width: 1000px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 20px;
}

.profile-card {
  .avatar-section {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    padding: 16px 0;

    :deep(.el-avatar) {
      background: linear-gradient(135deg, #409eff, #1677ff);
      border: 3px solid #e6f2ff;
    }

    h3 {
      margin: 0;
      font-size: 18px;
    }
  }
}

.nav-links {
  .nav-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 12px 14px;
    border-radius: 8px;
    cursor: pointer;
    color: var(--el-text-color-regular);
    transition: all 0.2s;

    &:hover {
      background: var(--el-fill-color-light);
      color: var(--el-color-primary);
    }

    &.active {
      background: var(--el-color-primary-light-9);
      color: var(--el-color-primary);
      font-weight: 600;
    }
  }
}

.records-card {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-weight: 600;
  }
}

.records-table {
  margin-bottom: 8px;
}

.apply-detail {
  margin: 16px 0 24px;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.04);
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  background: linear-gradient(90deg, var(--el-color-primary-light-9), #fff);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.detail-title {
  font-weight: 600;
  font-size: 15px;
}

.detail-body {
  padding: 18px;
}

.detail-section {
  margin-bottom: 18px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  margin-bottom: 12px;
  padding-left: 8px;
  border-left: 3px solid var(--el-color-primary);
}

.content-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.content-item {
  background: var(--el-fill-color-light);
  border-radius: 8px;
  padding: 10px 12px;
}

.content-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.content-value {
  font-size: 14px;
  color: var(--el-text-color-primary);
  word-break: break-word;
}

.detail-meta {
  display: flex;
  gap: 32px;
  padding-top: 14px;
  border-top: 1px dashed var(--el-border-color);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.meta-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.meta-value {
  font-size: 14px;
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.fade-enter-active,
.fade-leave-active {
  transition: all 0.25s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

.detail-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-secondary);
  padding: 12px 0;
}

.timeline-node {
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 4px;
}

.timeline-approver {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin-bottom: 4px;

  .label {
    color: var(--el-text-color-secondary);
  }
}

.timeline-status {
  margin-bottom: 6px;
}

.timeline-comment {
  background: var(--el-fill-color-light);
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  word-break: break-word;
}
</style>
