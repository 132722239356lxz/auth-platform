<template>
  <div class="app-container">
    <el-tabs v-model="activeTab" @tab-change="loadData">
      <el-tab-pane label="我的申请" name="applications" />
      <el-tab-pane label="待我审批" name="pending" />
      <el-tab-pane label="我的审批" name="myRecords" />
    </el-tabs>

    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="50" />

        <!-- 通用列：标题 / 申请人 -->
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="applicant" label="申请人" width="100" />

        <!-- 我的审批记录专用列 -->
        <template v-if="activeTab === 'myRecords'">
          <el-table-column prop="nodeName" label="审批节点" width="120" />
          <el-table-column prop="action" label="审批动作" width="100">
            <template #default="{ row }">
              <el-tag :type="actionTag(row.action)" size="small">{{ actionText(row.action) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="comment" label="审批意见" min-width="150" show-overflow-tooltip />
          <el-table-column label="审批时间" width="170">
            <template #default="{ row }">
              {{ formatTime(row.approveTime) }}
            </template>
          </el-table-column>
          <el-table-column prop="instanceStatus" label="流程状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.instanceStatus)" size="small">{{ statusText(row.instanceStatus) }}</el-tag>
            </template>
          </el-table-column>
        </template>

        <!-- 申请 / 待办 列 -->
        <template v-else>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="当前节点" width="120">
            <template #default="{ row }">
              {{ row.currentNodeName || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="当前审批人" width="120">
            <template #default="{ row }">
              {{ row.currentApprover || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">
              {{ formatTime(row.createTime) }}
            </template>
          </el-table-column>
        </template>

        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="handleDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getMyApplications, getMyPending, getMyRecords } from '@/api/workflow'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const activeTab = ref('applications')
const loading = ref(false)
const list = ref<any[]>([])

const TAB_STORAGE_KEY = 'workflow_active_tab'

function statusText(status: string) {
  return { PENDING: '审批中', APPROVED: '已通过', REJECTED: '已驳回', WITHDRAWN: '已撤回' }[status] || status
}

function statusTag(status: string) {
  return { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', WITHDRAWN: 'info' }[status] as any || 'info'
}

function actionText(action: string) {
  return { APPROVE: '通过', REJECT: '驳回', TRANSFER: '转交' }[action] || action
}

function actionTag(action: string) {
  return { APPROVE: 'success', REJECT: 'danger', TRANSFER: 'warning' }[action] as any || 'info'
}

function formatTime(time?: string) {
  if (!time) return '-'
  return time.replace('T', ' ').slice(0, 19)
}

async function loadData() {
  loading.value = true
  try {
    const username = userStore.username || 'admin'
    let res
    if (activeTab.value === 'applications') {
      res = await getMyApplications(username)
    } else if (activeTab.value === 'pending') {
      res = await getMyPending(username)
    } else {
      res = await getMyRecords(username)
    }
    list.value = res?.data || []
  } catch { list.value = [] }
  finally { loading.value = false }
}

function handleDetail(row: any) {
  sessionStorage.setItem(TAB_STORAGE_KEY, activeTab.value)
  router.push(`/workflow/detail/${row.instanceId || row.id}`)
}

onMounted(() => {
  const tabFromQuery = route.query.tab as string
  const tabFromStorage = sessionStorage.getItem(TAB_STORAGE_KEY)
  const validTabs = ['applications', 'pending', 'myRecords']
  const initialTab = validTabs.includes(tabFromQuery)
    ? tabFromQuery
    : validTabs.includes(tabFromStorage || '')
      ? tabFromStorage
      : 'applications'
  activeTab.value = initialTab || 'applications'
  if (activeTab.value !== tabFromStorage) {
    sessionStorage.setItem(TAB_STORAGE_KEY, activeTab.value)
  }
  loadData()
})
</script>
