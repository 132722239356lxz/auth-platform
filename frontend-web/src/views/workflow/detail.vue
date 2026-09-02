<template>
  <div class="app-container">
    <el-card shadow="never" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>流程详情</span>
          <el-button size="small" @click="handleBack">返回</el-button>
        </div>
      </template>

      <el-descriptions :column="2" border v-if="detail?.instance">
        <el-descriptions-item label="标题">{{ detail.instance.title }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(detail.instance.status)" size="small">{{ statusText(detail.instance.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="申请人">{{ detail.instance.applicant }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.instance.createTime }}</el-descriptions-item>
        <el-descriptions-item label="当前节点">{{ detail.instance.currentNodeName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前审批人">{{ detail.instance.currentApprover || '-' }}</el-descriptions-item>
      </el-descriptions>

      <h3 style="margin: 20px 0 12px">申请内容</h3>
      <el-card v-if="detail?.instance" shadow="never" class="apply-content-card">
        <el-empty v-if="applyContentFields.length === 0" description="暂无申请内容" />
        <el-form v-else label-width="140px" label-position="right" class="apply-form">
          <el-row :gutter="16">
            <el-col
              v-for="item in applyContentFields"
              :key="item.field"
              :span="item.full ? 24 : 12"
            >
              <el-form-item :label="item.field">
                <div class="content-value">{{ item.value }}</div>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-card>

      <!-- 审批记录 -->
      <h3 style="margin: 20px 0 12px">审批记录</h3>
      <el-timeline v-if="detail?.tasks?.length">
        <el-timeline-item
          v-for="task in detail.tasks"
          :key="task.id"
          :timestamp="taskTimelineTime(task)"
          :type="taskTimelineType(task.status)"
        >
          <h4>{{ task.nodeName }} - {{ task.approver }}</h4>
          <p>状态: {{ taskStatusText(task.status) }}</p>
          <p v-if="task.comment">意见: {{ task.comment }}</p>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无审批记录" />

      <!-- 审批操作 -->
      <div v-if="canApprove" style="margin-top: 24px">
        <el-divider />
        <h3>审批操作</h3>
        <el-form :model="approvalForm" label-width="80px" style="margin-top: 16px">
          <el-form-item label="审批意见">
            <el-input v-model="approvalForm.comment" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item>
            <el-button type="success" @click="handleApprove('APPROVE')">通过</el-button>
            <el-button type="danger" @click="handleApprove('REJECT')">驳回</el-button>
            <el-button type="warning" @click="handleApprove('TRANSFER')">转交</el-button>
          </el-form-item>
          <el-form-item v-if="approvalForm.action === 'TRANSFER'" label="转交给">
            <el-input v-model="approvalForm.transferTo" placeholder="请输入用户名" />
          </el-form-item>
        </el-form>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getWorkflowDetail, approveWorkflow, getWorkflowForms } from '@/api/workflow'
import { useUserStore } from '@/stores/user'
import type { WorkflowDetail, WorkflowTask, WorkflowForm, FormFieldSchema } from '@/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const detail = ref<WorkflowDetail | null>(null)
const formSchema = ref<WorkflowForm | null>(null)

const approvalForm = reactive({
  taskId: 0, action: '' as 'APPROVE' | 'REJECT' | 'TRANSFER', comment: '', transferTo: ''
})

const parsedApplyContent = computed(() => {
  try {
    return detail.value?.instance?.applyContent
      ? JSON.parse(detail.value.instance.applyContent)
      : {}
  } catch {
    return {}
  }
})

const schemaFields = computed((): FormFieldSchema[] => {
  if (!formSchema.value?.schemaJson) return []
  try {
    const parsed = JSON.parse(formSchema.value.schemaJson)
    if (Array.isArray(parsed)) return parsed
    if (parsed && Array.isArray(parsed.fields)) return parsed.fields
    return []
  } catch {
    return []
  }
})

const applyContentFields = computed(() => {
  const content = parsedApplyContent.value
  const fields = schemaFields.value
  if (fields.length > 0) {
    return fields.map((f) => ({
      field: f.field,
      value: formatValue(content[f.field], f),
      full: f.type === 'textarea'
    }))
  }
  // 未绑定表单定义时，回退到原始 key-value 展示
  return Object.keys(content).map((k) => ({
    field: k,
    value: String(content[k] ?? '-'),
    full: false
  }))
})

function formatValue(value: any, field: FormFieldSchema): string {
  if (value === undefined || value === null || value === '') return '-'
  if (field.type === 'select' && field.options?.length) {
    const option = field.options.find((o) => String(o.value) === String(value))
    return option?.label ?? String(value)
  }
  if (Array.isArray(value)) return value.join(', ')
  return String(value)
}

const canApprove = computed(() => {
  if (!detail.value?.instance || detail.value.instance.status !== 'PENDING') return false
  const pendingTask = detail.value.tasks?.find(
    (t: WorkflowTask) => t.status === 'PENDING' && t.approver === userStore.username
  )
  if (pendingTask) {
    approvalForm.taskId = pendingTask.id
    return true
  }
  return false
})

function statusText(status: string) {
  return { PENDING: '审批中', APPROVED: '已通过', REJECTED: '已驳回', WITHDRAWN: '已撤回' }[status] || status
}

function statusTag(status: string) {
  return { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', WITHDRAWN: 'info' }[status] as any || 'info'
}

function taskStatusText(status: string) {
  return { PENDING: '待处理', APPROVED: '已通过', REJECTED: '已驳回', TRANSFERRED: '已转交' }[status] || status
}

function taskTimelineType(status: string) {
  return { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', TRANSFERRED: 'primary' }[status] as any || 'info'
}

function taskTimelineTime(task: WorkflowTask) {
  if (task.approveTime) return task.approveTime
  if (task.createTime) return task.createTime
  return ''
}

function handleBack() {
  const savedTab = sessionStorage.getItem('workflow_active_tab')
  const validTabs = ['applications', 'pending', 'myRecords']
  const tab = validTabs.includes(savedTab || '') ? savedTab : undefined
  if (tab) {
    router.push({ path: '/workflow/list', query: { tab } })
  } else {
    router.push('/workflow/list')
  }
}

async function loadFormSchema(definitionKey: string) {
  if (!definitionKey) return
  try {
    const res = await getWorkflowForms({ definitionKey })
    const list = res?.data || []
    formSchema.value = list.find((f: WorkflowForm) => f.definitionKey === definitionKey) || null
  } catch {
    formSchema.value = null
  }
}

async function loadData() {
  const id = Number(route.params.id)
  if (!id) return
  loading.value = true
  try {
    const res = await getWorkflowDetail(id)
    detail.value = res?.data || null
    const definitionKey = detail.value?.definition?.definitionKey
    if (definitionKey) {
      await loadFormSchema(definitionKey)
    }
  } catch { detail.value = null }
  finally { loading.value = false }
}

async function handleApprove(action: 'APPROVE' | 'REJECT' | 'TRANSFER') {
  approvalForm.action = action
  if (action === 'TRANSFER' && !approvalForm.transferTo) {
    ElMessage.warning('请输入转交人用户名')
    return
  }
  try {
    await approveWorkflow({
      taskId: approvalForm.taskId,
      action,
      comment: approvalForm.comment,
      transferTo: approvalForm.transferTo || undefined
    })
    ElMessage.success('操作成功')
    loadData()
  } catch { /* */ }
}

onMounted(loadData)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.apply-content-card {
  background: #fafafa;
}
.apply-form :deep(.el-form-item__label) {
  color: #606266;
  font-weight: 500;
}
.content-value {
  color: #303133;
  word-break: break-word;
  line-height: 1.6;
}
</style>
