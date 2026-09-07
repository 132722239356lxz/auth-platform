<template>
  <div class="app-container">
    <el-card shadow="never" class="filter-card">
      <el-form :model="queryForm" inline>
        <el-form-item label="模板编码">
          <el-input v-model="queryForm.templateCode" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="模板名称">
          <el-input v-model="queryForm.keyword" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="渠道">
          <el-select v-model="queryForm.channel" placeholder="请选择" clearable style="width: 150px">
            <el-option
              v-for="opt in channelOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            @change="handleDateChange"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新建模板</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table :data="templates" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="templateCode" label="模板编码" width="160" />
        <el-table-column prop="templateName" label="模板名称" min-width="160" />
        <el-table-column prop="channel" label="渠道" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="channelType(row.channel)">
              {{ channelLabel(row.channel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="titleTemplate" label="标题模板" min-width="180" show-overflow-tooltip />
        <el-table-column prop="contentTemplate" label="内容模板" min-width="200" show-overflow-tooltip />
        <el-table-column label="创建时间" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'danger' : 'success'" size="small">
              {{ row.status === 0 ? '停用' : '启用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="queryForm.page"
          v-model:page-size="queryForm.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑消息模板' : '新建消息模板'" width="600px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="模板编码" :rules="[{ required: true, message: '请输入模板编码' }]">
          <el-input v-model="form.templateCode" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="模板名称">
          <el-input v-model="form.templateName" />
        </el-form-item>
        <el-form-item label="发送渠道">
          <el-select v-model="form.channel" style="width: 100%">
            <el-option
              v-for="opt in channelOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="标题模板">
          <el-input v-model="form.titleTemplate" placeholder="支持变量: {{title}}" />
        </el-form-item>
        <el-form-item label="内容模板">
          <el-input v-model="form.contentTemplate" type="textarea" :rows="5"
            placeholder="支持变量: {{applicant}}, {{result}}, {{comment}} 等" />
        </el-form-item>
        <el-form-item label="变量说明">
          <el-input v-model="form.variables" type="textarea" :rows="2"
            placeholder='JSON格式,如: [{"name":"applicant","desc":"申请人"}]' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">{{ isEdit ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMessageTemplates, createMessageTemplate, updateMessageTemplate, deleteMessageTemplate } from '@/api/message'
import type { MessageTemplateVO, MessageTemplateQueryParams } from '@/types'
import { channelOptions, channelLabel, channelType } from '@/utils/channel'
import { formatTime } from '@/utils/format'
const loading = ref(false)
const saving = ref(false)
const templates = ref<MessageTemplateVO[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const dateRange = ref<[string, string] | null>(null)

const queryForm = reactive<MessageTemplateQueryParams>({
  keyword: '',
  templateCode: '',
  channel: undefined,
  status: undefined,
  page: 1,
  size: 20
})

const defaultForm = () => ({
  templateCode: '', templateName: '', channel: 'IN_APP',
  titleTemplate: '', contentTemplate: '', variables: '', status: 1
})
const form = reactive(defaultForm())

function buildQueryParams(): MessageTemplateQueryParams {
  const params: MessageTemplateQueryParams = {
    keyword: queryForm.keyword || undefined,
    templateCode: queryForm.templateCode || undefined,
    channel: queryForm.channel,
    status: queryForm.status,
    page: queryForm.page,
    size: queryForm.size
  }
  if (dateRange.value && dateRange.value.length === 2) {
    params.startTime = dateRange.value[0]
    params.endTime = dateRange.value[1]
  }
  return params
}

async function loadData() {
  loading.value = true
  try {
    const res = await getMessageTemplates(buildQueryParams())
    templates.value = res?.data?.list || []
    total.value = res?.data?.total || 0
  } catch { 
    templates.value = []
    total.value = 0
  }
  finally { loading.value = false }
}

function handleSearch() {
  queryForm.page = 1
  loadData()
}

function handleReset() {
  queryForm.keyword = ''
  queryForm.templateCode = ''
  queryForm.channel = undefined
  queryForm.status = undefined
  queryForm.page = 1
  dateRange.value = null
  loadData()
}

function handleSizeChange(size: number) {
  queryForm.size = size
  queryForm.page = 1
  loadData()
}

function handlePageChange(page: number) {
  queryForm.page = page
  loadData()
}

function handleDateChange(val: [string, string] | null) {
  dateRange.value = val
}

function handleAdd() {
  isEdit.value = false
  Object.assign(form, defaultForm())
  dialogVisible.value = true
}

function handleEdit(row: MessageTemplateVO) {
  isEdit.value = true
  Object.assign(form, {
    templateCode: row.templateCode,
    templateName: row.templateName,
    channel: row.channel,
    titleTemplate: row.titleTemplate,
    contentTemplate: row.contentTemplate,
    variables: row.variables || '',
    status: row.status ?? 1
  })
  dialogVisible.value = true
}

async function handleSave() {
  saving.value = true
  try {
    if (isEdit.value) {
      await updateMessageTemplate(form.templateCode, {
        templateName: form.templateName,
        channel: form.channel,
        titleTemplate: form.titleTemplate,
        contentTemplate: form.contentTemplate,
        variables: form.variables,
        status: form.status
      })
      ElMessage.success('模板更新成功')
    } else {
      await createMessageTemplate(form)
      ElMessage.success('模板创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch { /* */ }
  finally { saving.value = false }
}

async function handleDelete(row: MessageTemplateVO) {
  try {
    await ElMessageBox.confirm(`确定删除模板 "${row.templateName}" 吗？`, '提示', { type: 'warning' })
    await deleteMessageTemplate(row.templateCode)
    ElMessage.success('删除成功')
    loadData()
  } catch { /* */ }
}

onMounted(loadData)
</script>

<style scoped>
.filter-card { margin-bottom: 16px; }
</style>
