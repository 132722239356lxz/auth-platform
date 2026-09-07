<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-container">
        <el-input
          v-model="searchForm.keyword"
          placeholder="标题 / 内容"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="searchForm.noticeType" placeholder="类型" clearable style="width: 130px">
          <el-option label="公告" value="ANNOUNCEMENT" />
          <el-option label="通知" value="NOTICE" />
          <el-option label="告警" value="WARNING" />
          <el-option label="维护" value="MAINTAIN" />
        </el-select>
        <el-select v-model="searchForm.enabled" placeholder="状态" clearable style="width: 110px">
          <el-option label="启用" :value="true" />
          <el-option label="禁用" :value="false" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button v-permission="'system:notice:add'" type="success" @click="handleAdd">发布公告</el-button>
      </div>
    </el-card>

    <!-- 公告列表 -->
    <el-card shadow="never">
      <el-table :data="noticeList" v-loading="loading" border stripe style="width: 100%">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="noticeType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.noticeType)">{{ typeLabel(row.noticeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="priorityTag(row.priority)">{{ priorityLabel(row.priority) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="top" label="置顶" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.top" type="danger" size="small">是</el-tag>
            <span v-else style="color: #c0c4cc">否</span>
          </template>
        </el-table-column>
        <el-table-column prop="publisherName" label="发布人" width="110" />
        <el-table-column prop="publishTime" label="发布时间" width="170">
          <template #default="{ row }">{{ formatTime(row.publishTime) }}</template>
        </el-table-column>
        <el-table-column prop="expireTime" label="过期时间" width="170">
          <template #default="{ row }">{{ formatTime(row.expireTime) || '-' }}</template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="readCount" label="阅读数" width="80" align="center" />
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-permission="'system:notice:edit'" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'system:notice:delete'" size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        class="pagination-container"
        @size-change="handleSearch"
        @current-change="handleSearch"
      />
    </el-card>

    <!-- 新增/编辑公告弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑公告' : '发布公告'" width="650px">
      <el-form ref="formRef" :model="noticeForm" :rules="formRules" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="noticeForm.title" placeholder="请输入公告标题" />
        </el-form-item>
        <el-form-item label="类型" prop="noticeType">
          <el-select v-model="noticeForm.noticeType" placeholder="请选择类型" style="width: 100%">
            <el-option label="公告" value="ANNOUNCEMENT" />
            <el-option label="通知" value="NOTICE" />
            <el-option label="告警" value="WARNING" />
            <el-option label="维护" value="MAINTAIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-radio-group v-model="noticeForm.priority">
            <el-radio :label="1">普通</el-radio>
            <el-radio :label="2">重要</el-radio>
            <el-radio :label="3">紧急</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="noticeForm.content" type="textarea" :rows="6" placeholder="请输入公告内容" />
        </el-form-item>
        <el-form-item label="发布时间">
          <el-date-picker
            v-model="noticeForm.publishTime"
            type="datetime"
            placeholder="选择发布时间"
            style="width: 100%"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="noticeForm.expireTime"
            type="datetime"
            placeholder="选择过期时间（可选）"
            style="width: 100%"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="置顶">
          <el-switch v-model="noticeForm.top" active-text="是" inactive-text="否" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="noticeForm.enabled" active-text="是" inactive-text="否" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getNoticesPage, createNotice, updateNotice, deleteNotice } from '@/api/system'
import { formatTime } from '@/utils/format'
import type { NoticeInfo } from '@/types'

const loading = ref(false)
const noticeList = ref<NoticeInfo[]>([])
const searchForm = reactive({ keyword: '', noticeType: '', enabled: undefined as boolean | undefined })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })

const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const noticeForm = reactive({
  title: '',
  content: '',
  noticeType: 'ANNOUNCEMENT',
  priority: 1,
  top: false,
  publishTime: '',
  expireTime: '',
  enabled: true,
})
const formRules: FormRules = {
  title: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  noticeType: [{ required: true, message: '请选择公告类型', trigger: 'change' }],
  content: [{ required: true, message: '请输入公告内容', trigger: 'blur' }],
}

const typeMap: Record<string, { label: string; tag: string }> = {
  ANNOUNCEMENT: { label: '公告', tag: 'primary' },
  NOTICE: { label: '通知', tag: 'success' },
  WARNING: { label: '告警', tag: 'danger' },
  MAINTAIN: { label: '维护', tag: 'warning' },
}

const priorityMap: Record<number, { label: string; tag: string }> = {
  1: { label: '普通', tag: 'info' },
  2: { label: '重要', tag: 'warning' },
  3: { label: '紧急', tag: 'danger' },
}

function typeLabel(type?: string) {
  return typeMap[type || '']?.label || type || '-'
}
function typeTag(type?: string) {
  return typeMap[type || '']?.tag || 'info'
}
function priorityLabel(priority?: number) {
  return priorityMap[priority ?? 1]?.label || '普通'
}
function priorityTag(priority?: number) {
  return priorityMap[priority ?? 1]?.tag || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res = await getNoticesPage({
      keyword: searchForm.keyword || undefined,
      noticeType: searchForm.noticeType || undefined,
      enabled: searchForm.enabled,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    const data = res?.data
    noticeList.value = data?.records || []
    pagination.total = data?.total || 0
  } catch {
    noticeList.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  loadData()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.noticeType = ''
  searchForm.enabled = undefined
  pagination.page = 1
  loadData()
}

function resetForm() {
  noticeForm.title = ''
  noticeForm.content = ''
  noticeForm.noticeType = 'ANNOUNCEMENT'
  noticeForm.priority = 1
  noticeForm.top = false
  noticeForm.publishTime = ''
  noticeForm.expireTime = ''
  noticeForm.enabled = true
  formRef.value?.resetFields()
}

function handleAdd() {
  isEdit.value = false
  editId.value = null
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: NoticeInfo) {
  isEdit.value = true
  editId.value = row.id
  noticeForm.title = row.title
  noticeForm.content = row.content
  noticeForm.noticeType = row.noticeType || 'ANNOUNCEMENT'
  noticeForm.priority = row.priority || 1
  noticeForm.top = row.top || false
  noticeForm.publishTime = (row.publishTime || '').replace(' ', 'T').replace(/\.\d+$/, '') || ''
  noticeForm.expireTime = (row.expireTime || '').replace(' ', 'T').replace(/\.\d+$/, '') || ''
  noticeForm.enabled = row.enabled
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = { ...noticeForm }
    if (isEdit.value && editId.value) {
      await updateNotice(editId.value, payload)
      ElMessage.success('公告更新成功')
    } else {
      await createNotice(payload)
      ElMessage.success('公告发布成功')
    }
    dialogVisible.value = false
    loadData()
  } catch { /* */ }
  finally { saving.value = false }
}

async function handleDelete(row: NoticeInfo) {
  try {
    await ElMessageBox.confirm(`确定删除公告 "${row.title}" 吗？`, '提示', { type: 'warning' })
    await deleteNotice(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch { /* cancelled */ }
}

onMounted(loadData)
</script>

<style scoped>
.filter-card { margin-bottom: 16px; }
.filter-container { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
</style>
