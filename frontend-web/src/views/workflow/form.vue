<template>
  <div class="app-container">
    <el-card shadow="never" class="filter-card">
      <el-form :model="query" inline size="default">
        <el-form-item label="表单Key">
          <el-input v-model="query.formKey" placeholder="请输入表单Key" clearable />
        </el-form-item>
        <el-form-item label="表单名称">
          <el-input v-model="query.formName" placeholder="请输入表单名称" clearable />
        </el-form-item>
        <el-form-item label="绑定流程">
          <el-select
            v-model="query.definitionKey"
            placeholder="请选择流程定义"
            clearable
            filterable
            style="width: 200px"
          >
            <el-option
              v-for="def in definitionOptions"
              :key="def.definitionKey"
              :label="`${def.definitionName} (${def.definitionKey})`"
              :value="def.definitionKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="业务类型">
          <el-input v-model="query.applyType" placeholder="请输入业务类型" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="fetchData">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>审批表单维护</span>
          <el-button v-permission="'workflow:form:add'" type="primary" :icon="Plus" @click="openCreate">
            新增表单
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="formKey" label="表单Key" width="180" />
        <el-table-column prop="formName" label="表单名称" width="160" />
        <el-table-column label="绑定流程" width="220">
          <template #default="{ row }">
            {{ getDefinitionLabel(row.definitionKey) }}
          </template>
        </el-table-column>
        <el-table-column prop="applyType" label="业务类型" width="160" />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" :icon="View" @click="openPreview(row)">预览</el-button>
            <el-button v-permission="'workflow:form:edit'" link type="primary" :icon="Edit"
              @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="'workflow:form:edit'" link type="warning" :icon="Switch"
              @click="toggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button v-permission="'workflow:form:delete'" link type="danger" :icon="Delete"
              @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="92vw"
      class="form-dialog"
      top="2vh"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="表单Key" prop="formKey">
              <el-input v-model="form.formKey" :disabled="isEdit" placeholder="如 SUBSYSTEM_VISIBILITY" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="表单名称" prop="formName">
              <el-input v-model="form.formName" placeholder="如 子系统可见权限申请" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="绑定流程" prop="definitionKey">
              <el-select
                v-model="form.definitionKey"
                placeholder="请选择流程定义"
                clearable
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="def in definitionOptions"
                  :key="def.definitionKey"
                  :label="`${def.definitionName} (${def.definitionKey})`"
                  :value="def.definitionKey"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="业务类型" prop="applyType">
              <el-input v-model="form.applyType" placeholder="如 SUBSYSTEM_VISIBILITY" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="图标" prop="icon">
              <el-input v-model="form.icon" placeholder="Element Plus 图标名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序" prop="sortOrder">
              <el-input-number v-model="form.sortOrder" :min="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">表单字段</el-divider>

        <div class="editor-split">
          <div class="editor-pane field-pane">
            <div v-for="(field, idx) in formFields" :key="idx" class="field-card">
              <div class="field-header">
                <span class="field-title">字段 {{ idx + 1 }}</span>
                <el-button type="danger" size="small" circle @click="removeField(idx)">
                  <el-icon><Close /></el-icon>
                </el-button>
              </div>
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item :label="`字段名`" :prop="`schemaJson.field-${idx}`">
                    <el-input v-model="field.field" placeholder="如 leaveDays" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item :label="`显示名`" :prop="`schemaJson.label-${idx}`">
                    <el-input v-model="field.label" placeholder="如 请假天数" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="类型" :prop="`schemaJson.type-${idx}`">
                    <el-select v-model="field.type" placeholder="请选择" style="width: 100%">
                      <el-option label="文本" value="text" />
                      <el-option label="多行文本" value="textarea" />
                      <el-option label="数字" value="number" />
                      <el-option label="下拉选择" value="select" />
                      <el-option label="日期" value="date" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="是否必填">
                    <el-checkbox v-model="field.required">必填</el-checkbox>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="占位提示">
                    <el-input v-model="field.placeholder" placeholder="请输入提示文字" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="最大长度">
                    <el-input-number v-model="field.maxlength" :min="0" style="width: 100%" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row v-if="field.type === 'select'" :gutter="12">
                <el-col :span="24">
                  <el-form-item label="选项">
                    <div class="options-list">
                      <div v-for="(opt, oIdx) in field.options" :key="oIdx" class="option-row">
                        <el-input v-model="opt.label" placeholder="显示文字" />
                        <el-input v-model="opt.value" placeholder="值" />
                        <el-button type="danger" size="small" circle @click="removeOption(field, oIdx)">
                          <el-icon><Close /></el-icon>
                        </el-button>
                      </div>
                      <el-button link type="primary" :icon="Plus" @click="addOption(field)">添加选项</el-button>
                    </div>
                  </el-form-item>
                </el-col>
              </el-row>
            </div>

            <el-button type="primary" :icon="Plus" @click="addField">添加字段</el-button>
          </div>

          <div class="editor-pane preview-pane">
            <div class="preview-form">
              <div class="preview-form-title">{{ form.formName || '未命名表单' }}</div>
              <el-empty v-if="formFields.length === 0" description="暂无字段，请在左侧添加" />
              <el-form v-else :model="previewValues" label-width="110px" label-position="right">
                <el-form-item
                  v-for="(field, idx) in formFields"
                  :key="idx"
                  :label="field.label || field.field"
                  :required="field.required"
                >
                  <el-input
                    v-if="field.type === 'text'"
                    v-model="previewValues[field.field]"
                    :placeholder="field.placeholder"
                    :maxlength="field.maxlength || undefined"
                    style="width: 100%"
                  />
                  <el-input
                    v-else-if="field.type === 'textarea'"
                    v-model="previewValues[field.field]"
                    type="textarea"
                    :rows="3"
                    :placeholder="field.placeholder"
                    :maxlength="field.maxlength || undefined"
                    style="width: 100%"
                  />
                  <el-input-number
                    v-else-if="field.type === 'number'"
                    v-model="previewValues[field.field]"
                    :placeholder="field.placeholder"
                    style="width: 100%"
                  />
                  <el-select
                    v-else-if="field.type === 'select'"
                    v-model="previewValues[field.field]"
                    :placeholder="field.placeholder || '请选择'"
                    clearable
                    style="width: 100%"
                  >
                    <el-option
                      v-for="(opt, oIdx) in (field.options || [])"
                      :key="oIdx"
                      :label="opt.label"
                      :value="opt.value"
                    />
                  </el-select>
                  <el-date-picker
                    v-else-if="field.type === 'date'"
                    v-model="previewValues[field.field]"
                    type="date"
                    :placeholder="field.placeholder || '请选择日期'"
                    value-format="YYYY-MM-DD"
                    style="width: 100%"
                  />
                  <span v-else>未知类型</span>
                </el-form-item>
              </el-form>
            </div>
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 表单预览弹窗 -->
    <el-dialog v-model="previewVisible" :title="previewTitle" width="560px" destroy-on-close>
      <div class="preview-form">
        <div class="preview-form-title">{{ previewFormTitle }}</div>
        <el-form :model="previewValues" label-width="110px" label-position="right">
          <el-form-item
            v-for="(field, idx) in previewFields"
            :key="idx"
            :label="field.label || field.field"
            :required="field.required"
          >
            <el-input
              v-if="field.type === 'text'"
              v-model="previewValues[field.field]"
              :placeholder="field.placeholder"
              :maxlength="field.maxlength || undefined"
              style="width: 100%"
            />
            <el-input
              v-else-if="field.type === 'textarea'"
              v-model="previewValues[field.field]"
              type="textarea"
              :rows="3"
              :placeholder="field.placeholder"
              :maxlength="field.maxlength || undefined"
              style="width: 100%"
            />
            <el-input-number
              v-else-if="field.type === 'number'"
              v-model="previewValues[field.field]"
              :placeholder="field.placeholder"
              style="width: 100%"
            />
            <el-select
              v-else-if="field.type === 'select'"
              v-model="previewValues[field.field]"
              :placeholder="field.placeholder || '请选择'"
              clearable
              style="width: 100%"
            >
              <el-option
                v-for="(opt, oIdx) in (field.options || [])"
                :key="oIdx"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <el-date-picker
              v-else-if="field.type === 'date'"
              v-model="previewValues[field.field]"
              type="date"
              :placeholder="field.placeholder || '请选择日期'"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
            <span v-else>未知类型</span>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button type="primary" @click="previewVisible = false">关闭预览</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { Plus, Edit, Delete, Switch, Search, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, FormInstance, FormRules } from 'element-plus'
import {
  getWorkflowForms,
  createWorkflowForm,
  updateWorkflowForm,
  updateWorkflowFormStatus,
  deleteWorkflowForm,
  getWorkflowDefinitions
} from '@/api/workflow'
import type { WorkflowForm, WorkflowDefinition, FormFieldSchema } from '@/types'

const loading = ref(false)
const submitting = ref(false)
const rows = ref<WorkflowForm[]>([])
const definitionOptions = ref<WorkflowDefinition[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()

// 编辑弹窗：左侧字段配置 + 右侧实时预览

// 预览
const previewVisible = ref(false)
const previewTitle = ref('')
const previewFormTitle = ref('')
const previewFields = ref<FormFieldSchema[]>([])
const previewValues = reactive<Record<string, any>>({})

const query = reactive({
  formKey: '',
  formName: '',
  definitionKey: '',
  applyType: '',
  status: undefined as number | undefined
})

const emptyForm = (): WorkflowForm => ({
  formKey: '',
  formName: '',
  definitionKey: '',
  schemaJson: '',
  icon: '',
  sortOrder: 0,
  status: 1,
  applyType: ''
})

const form = reactive<WorkflowForm>(emptyForm())
const formFields = ref<FormFieldSchema[]>([])

const rules = reactive<FormRules<WorkflowForm>>({
  formKey: [{ required: true, message: '请输入表单Key', trigger: 'blur' }],
  formName: [{ required: true, message: '请输入表单名称', trigger: 'blur' }],
  definitionKey: [{ required: true, message: '请选择绑定流程', trigger: 'change' }],
  applyType: [{ required: true, message: '请输入业务类型', trigger: 'blur' }]
})

function parseFieldsFromSchema() {
  try {
    const parsed = form.schemaJson ? JSON.parse(form.schemaJson) : null
    if (Array.isArray(parsed)) {
      formFields.value = parsed
    } else if (parsed && Array.isArray(parsed.fields)) {
      formFields.value = parsed.fields
    } else {
      formFields.value = []
    }
  } catch {
    formFields.value = []
  }
}

function syncSchemaFromFields() {
  form.schemaJson = JSON.stringify({ fields: formFields.value })
}

function addField() {
  const idx = formFields.value.length + 1
  formFields.value.push({
    field: `field_${idx}_${Date.now()}`,
    label: '',
    type: 'text',
    required: false,
    placeholder: '',
    options: []
  })
}

function removeField(idx: number) {
  const removed = formFields.value[idx]
  formFields.value.splice(idx, 1)
  if (removed?.field) {
    delete previewValues[removed.field]
  }
}

// 编辑弹窗预览：跟随字段变化同步预览值
watch(
  () => formFields.value.map(f => f.field).join(','),
  () => {
    formFields.value.forEach(f => {
      if (!(f.field in previewValues)) {
        previewValues[f.field] = f.type === 'number' ? undefined : ''
      }
    })
  }
)

watch(
  () => form.formName,
  () => {
    /* 预览标题响应式读取 form.formName，无需额外处理 */
  }
)

function addOption(field: FormFieldSchema) {
  if (!field.options) {
    field.options = []
  }
  field.options.push({ label: '', value: '' })
}

function removeOption(field: FormFieldSchema, idx: number) {
  field.options?.splice(idx, 1)
}

function loadDefinitions() {
  getWorkflowDefinitions().then(res => {
    definitionOptions.value = res.data ?? []
  })
}

function getDefinitionLabel(definitionKey: string) {
  const def = definitionOptions.value.find(d => d.definitionKey === definitionKey)
  return def ? `${def.definitionName} (${definitionKey})` : definitionKey
}

function fetchData() {
  loading.value = true
  getWorkflowForms({
    formKey: query.formKey || undefined,
    formName: query.formName || undefined,
    definitionKey: query.definitionKey || undefined,
    applyType: query.applyType || undefined,
    status: query.status
  })
    .then((res) => {
      rows.value = res.data ?? []
    })
    .finally(() => {
      loading.value = false
    })
}

function resetQuery() {
  query.formKey = ''
  query.formName = ''
  query.definitionKey = ''
  query.applyType = ''
  query.status = undefined
  fetchData()
}

function openCreate() {
  isEdit.value = false
  dialogTitle.value = '新增表单'
  Object.assign(form, emptyForm())
  formFields.value = []
  dialogVisible.value = true
}

function openEdit(row: WorkflowForm) {
  isEdit.value = true
  dialogTitle.value = '编辑表单'
  Object.assign(form, JSON.parse(JSON.stringify(row)))
  parseFieldsFromSchema()
  dialogVisible.value = true
}

function submit() {
  formRef.value?.validate((valid) => {
    if (!valid) {
      return
    }
    // 校验字段名：非空且不重复
    const emptyField = formFields.value.find((f) => !f.field || !f.field.trim())
    if (emptyField) {
      ElMessage.error('字段名不能为空')
      return
    }
    const fieldKeys = formFields.value.map((f) => f.field.trim())
    if (new Set(fieldKeys).size !== fieldKeys.length) {
      ElMessage.error('字段名不能重复')
      return
    }
    syncSchemaFromFields()
    submitting.value = true
    const payload = { ...form }
    const task = isEdit.value
      ? updateWorkflowForm(form.formKey, payload)
      : createWorkflowForm(payload)
    task
      .then(() => {
        ElMessage.success('保存成功')
        dialogVisible.value = false
        fetchData()
      })
      .finally(() => {
        submitting.value = false
      })
  })
}

function toggleStatus(row: WorkflowForm) {
  const next = row.status === 1 ? 0 : 1
  updateWorkflowFormStatus(row.formKey, next).then(() => {
    ElMessage.success('状态已更新')
    fetchData()
  })
}

function handleDelete(row: WorkflowForm) {
  ElMessageBox.confirm(`确认删除表单「${row.formName}」(${row.formKey})？`, '提示', {
    type: 'warning'
  })
    .then(() => {
      deleteWorkflowForm(row.formKey).then(() => {
        ElMessage.success('已删除')
        fetchData()
      })
    })
    .catch(() => {})
}

function openPreview(row: WorkflowForm) {
  previewTitle.value = `表单预览 - ${row.formName}`
  previewFormTitle.value = row.formName
  let fields: FormFieldSchema[] = []
  try {
    const schema = row.schemaJson ? JSON.parse(row.schemaJson) : null
    if (schema && Array.isArray(schema.fields)) {
      fields = schema.fields
    }
  } catch {
    fields = []
  }
  previewFields.value = fields
  Object.keys(previewValues).forEach(k => delete previewValues[k])
  fields.forEach(f => {
    previewValues[f.field] = f.type === 'number' ? undefined : ''
  })
  previewVisible.value = true
}

onMounted(() => {
  loadDefinitions()
  fetchData()
})
</script>

<style scoped>
.filter-card {
  margin-bottom: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.form-dialog {
  margin-left: auto !important;
  margin-right: 16px !important;
  height: 96vh;
}

.form-dialog :deep(.el-dialog) {
  margin: 0 !important;
  height: 96vh;
  display: flex;
  flex-direction: column;
}

.form-dialog :deep(.el-dialog__body) {
  flex: 1;
  overflow: hidden;
}

.editor-split {
  display: flex;
  gap: 16px;
  align-items: stretch;
  margin-top: 8px;
  height: 100%;
}

.editor-pane {
  max-height: none;
  height: 100%;
  overflow-y: auto;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}

.field-pane {
  flex: 1 1 0;
  background: #fafafa;
}

.preview-pane {
  flex: 1 1 0;
  background: #fff;
}

.field-card :deep(.el-form-item__label) {
  color: #606266;
}

.field-card {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 12px 16px;
  margin-bottom: 12px;
  background: #fafafa;
}

.field-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.field-title {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}

.options-list {
  width: 100%;
}

.option-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.option-row .el-input {
  flex: 1;
}

.preview-form {
  padding: 8px 4px;
}

.preview-form-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  text-align: center;
  padding: 12px 0 20px;
  margin-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}

.preview-form :deep(.el-form-item) {
  margin-bottom: 22px;
}

.preview-form :deep(.el-select),
.preview-form :deep(.el-date-picker),
.preview-form :deep(.el-input-number) {
  width: 100%;
}
</style>
