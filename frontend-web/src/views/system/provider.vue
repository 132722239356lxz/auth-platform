<template>
  <div class="app-container">
    <div class="filter-container">
      <el-input
        v-model="queryParams.providerName"
        placeholder="供应商名称"
        style="width: 180px"
        clearable
        @keyup.enter="handleSearch"
      />
      <el-select
        v-model="queryParams.providerType"
        placeholder="供应商类型"
        clearable
        style="width: 140px; margin-left: 10px"
      >
        <el-option label="OpenAI" value="openai" />
        <el-option label="Azure" value="azure" />
        <el-option label="Anthropic" value="anthropic" />
        <el-option label="自定义" value="custom" />
      </el-select>
      <el-select
        v-model="queryParams.enabled"
        placeholder="状态"
        clearable
        style="width: 120px; margin-left: 10px"
      >
        <el-option label="启用" :value="true" />
        <el-option label="禁用" :value="false" />
      </el-select>
      <el-button type="primary" :icon="Search" style="margin-left: 10px" @click="handleSearch">
        查询
      </el-button>
      <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      <el-button
        v-permission="'system:ai-provider:add'"
        type="success"
        :icon="Plus"
        style="margin-left: auto"
        @click="handleAdd"
      >
        新增供应商
      </el-button>
    </div>

    <el-table v-loading="loading" :data="tableData" border stripe highlight-current-row
      :row-class-name="routingRowClass"
      @row-click="openRoutingPanel"
    >
      <el-table-column type="index" width="50" align="center" />
      <el-table-column prop="providerCode" label="供应商编码" width="130" />
      <el-table-column prop="providerName" label="供应商名称" width="140" />
      <el-table-column prop="providerType" label="类型" width="100" />
      <el-table-column label="主/备" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.isPrimary" type="success" effect="dark">主</el-tag>
          <el-tag v-else type="info">备</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="priority" label="优先级" width="80" align="center" />
      <el-table-column prop="baseUrl" label="Base URL" min-width="200" show-overflow-tooltip />
      <el-table-column prop="defaultModel" label="默认模型" width="140" show-overflow-tooltip />
      <el-table-column prop="embeddingModel" label="向量模型" width="160" show-overflow-tooltip />
      <el-table-column prop="timeoutMs" label="超时(ms)" width="100" align="center" />
      <el-table-column prop="maxRetries" label="重试" width="80" align="center" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-switch
            v-model="row.enabled"
            :disabled="!userStore.hasPermission('system:ai-provider:edit')"
            @change="(val: boolean) => handleToggleEnabled(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="160" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button
            v-permission="'system:ai-provider:test'"
            type="primary"
            link
            :icon="Pointer"
            @click="handleTest(row)"
          >
            测试
          </el-button>
          <el-button
            v-permission="'system:ai-provider:edit'"
            type="primary"
            link
            :icon="Edit"
            @click="handleEdit(row)"
          >
            编辑
          </el-button>
          <el-popconfirm title="确定删除该供应商吗？" @confirm="handleDelete(row)">
            <template #reference>
              <el-button
                v-permission="'system:ai-provider:delete'"
                type="danger"
                link
                :icon="Delete"
              >
                删除
              </el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-container">
      <el-pagination
        v-model:current-page="queryParams.page"
        v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </div>

    <!-- ==================== 路由配置展开面板 ==================== -->
    <el-card v-if="showRoutingPanel" class="routing-panel" shadow="hover">
      <template #header>
        <div class="routing-panel-header">
          <span class="routing-panel-title">
            <el-icon><Connection /></el-icon>
            &nbsp;复杂度路由配置 — {{ selectedProvider?.providerName }}
            <el-tag size="small" style="margin-left: 8px">{{ selectedProvider?.providerCode }}</el-tag>
          </span>
          <el-button text type="danger" :icon="Close" @click="closeRoutingPanel">收起</el-button>
        </div>
      </template>

      <el-table v-loading="routingLoading" :data="routingList" border size="small">
        <el-table-column prop="routingName" label="路由名称" width="140" />
        <el-table-column label="简单任务模型" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.simpleModel || '—' }}</template>
        </el-table-column>
        <el-table-column label="中等信息模型" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.mediumModel || '—' }}</template>
        </el-table-column>
        <el-table-column label="复杂任务模型" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.complexModel || '—' }}</template>
        </el-table-column>
        <el-table-column label="简单 Tokens" width="100" align="center">
          <template #default="{ row }">{{ row.simpleMaxTokens || '—' }}</template>
        </el-table-column>
        <el-table-column label="中等 Tokens" width="100" align="center">
          <template #default="{ row }">{{ row.mediumMaxTokens || '—' }}</template>
        </el-table-column>
        <el-table-column label="复杂 Tokens" width="100" align="center">
          <template #default="{ row }">{{ row.complexMaxTokens || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              size="small"
              @change="(val: boolean) => handleRoutingToggle(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="140" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="handleRoutingEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该路由配置吗？" @confirm="handleRoutingDelete(row)">
              <template #reference>
                <el-button size="small" type="danger" link>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 12px">
        <el-button
          v-permission="'system:ai-routing:add'"
          type="primary"
          size="small"
          :icon="Plus"
          @click="handleRoutingAdd"
        >
          新增路由
        </el-button>
      </div>
    </el-card>

    <!-- ==================== 路由配置编辑弹窗 ==================== -->
    <el-dialog
      v-model="routingDialogVisible"
      :title="routingDialogTitle"
      width="650px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form
        ref="routingFormRef"
        :model="routingForm"
        :rules="routingFormRules"
        label-width="110px"
      >
        <el-form-item label="路由名称" prop="routingName">
          <el-input v-model="routingForm.routingName" placeholder="如：默认路由" maxlength="128" />
        </el-form-item>
        <el-divider content-position="left">
          <el-tag type="success" size="small">简单任务</el-tag>
        </el-divider>
        <el-row :gutter="16">
          <el-col :span="16">
            <el-form-item label="模型" prop="simpleModel">
              <el-select
                v-model="routingForm.simpleModel"
                filterable
                allow-create
                clearable
                placeholder="选择或输入模型名"
                style="width: 100%"
              >
                <el-option
                  v-for="m in availableModels"
                  :key="m"
                  :label="m"
                  :value="m"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="Tokens" prop="simpleMaxTokens">
              <el-input-number v-model="routingForm.simpleMaxTokens" :min="1" :max="1000000" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">
          <el-tag type="warning" size="small">中等任务</el-tag>
        </el-divider>
        <el-row :gutter="16">
          <el-col :span="16">
            <el-form-item label="模型" prop="mediumModel">
              <el-select
                v-model="routingForm.mediumModel"
                filterable
                allow-create
                clearable
                placeholder="选择或输入模型名"
                style="width: 100%"
              >
                <el-option
                  v-for="m in availableModels"
                  :key="m"
                  :label="m"
                  :value="m"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="Tokens" prop="mediumMaxTokens">
              <el-input-number v-model="routingForm.mediumMaxTokens" :min="1" :max="1000000" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">
          <el-tag type="danger" size="small">复杂任务</el-tag>
        </el-divider>
        <el-row :gutter="16">
          <el-col :span="16">
            <el-form-item label="模型" prop="complexModel">
              <el-select
                v-model="routingForm.complexModel"
                filterable
                allow-create
                clearable
                placeholder="选择或输入模型名"
                style="width: 100%"
              >
                <el-option
                  v-for="m in availableModels"
                  :key="m"
                  :label="m"
                  :value="m"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="Tokens" prop="complexMaxTokens">
              <el-input-number v-model="routingForm.complexMaxTokens" :min="1" :max="1000000" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="启用" prop="enabled">
          <el-switch v-model="routingForm.enabled" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="routingForm.remark" type="textarea" :rows="2" maxlength="512" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="routingDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="routingSubmitting" @click="handleRoutingSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 供应商编辑弹窗（原内容） ==================== -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="720px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="120px"
      >
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="供应商编码" prop="providerCode">
              <el-input v-model="form.providerCode" placeholder="如 deepseek" :disabled="isEdit" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商名称" prop="providerName">
              <el-input v-model="form.providerName" placeholder="如 DeepSeek" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="供应商类型" prop="providerType">
              <el-select v-model="form.providerType" placeholder="请选择" style="width: 100%">
                <el-option label="OpenAI" value="openai" />
                <el-option label="Azure" value="azure" />
                <el-option label="Anthropic" value="anthropic" />
                <el-option label="自定义" value="custom" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="默认模型" prop="defaultModel">
              <el-input v-model="form.defaultModel" placeholder="如 deepseek-chat" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="向量模型" prop="embeddingModel">
              <el-input v-model="form.embeddingModel" placeholder="如 text-embedding-3-small" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="https://api.example.com/v1" />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="API Key" prop="apiKey">
              <el-input
                v-model="form.apiKey"
                type="password"
                show-password
                :placeholder="isEdit ? '留空保持原值' : '供应商 API Key'"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Secret Key" prop="secretKey">
              <el-input
                v-model="form.secretKey"
                type="password"
                show-password
                placeholder="可选"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="模型列表" prop="modelsText">
          <el-input
            v-model="form.modelsText"
            type="textarea"
            :rows="3"
            placeholder="可选，多个模型用英文逗号分隔，如 deepseek-chat,deepseek-reasoner"
          />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="超时(ms)" prop="timeoutMs">
              <el-input-number v-model="form.timeoutMs" :min="1000" :max="120000" :step="1000" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大重试" prop="maxRetries">
              <el-input-number v-model="form.maxRetries" :min="0" :max="10" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="温度参数" prop="temperature">
              <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="enabled">
              <el-switch v-model="form.enabled" active-text="启用" inactive-text="禁用" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="主供应商" prop="isPrimary">
              <el-switch v-model="form.isPrimary" active-text="主用" inactive-text="备用" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级" prop="priority">
              <el-input-number v-model="form.priority" :min="0" :max="999" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <div v-if="!form.isPrimary" class="form-tip">
          备用供应商按优先级数字从小到大依次尝试切换，建议 0 为最高优先级备用。
        </div>
        <div v-else class="form-tip">
          设为主供应商后，其他供应商的"主供应商"状态将被自动取消。
        </div>

        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注说明" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search, Refresh, Plus, Edit, Delete, Pointer, Connection, Close
} from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  getAiProviders, createAiProvider, updateAiProvider,
  deleteAiProvider, testAiProvider,
  getRoutingByProvider, createAiRouting, updateAiRouting,
  deleteAiRouting, toggleAiRouting
} from '@/api/system'
import type { AiProvider, AiProviderRequest, AiProviderQueryParams, ProviderTestResponse, AiRoutingInfo, AiRoutingRequest } from '@/types'

interface FormModel extends AiProviderRequest {
  modelsText: string
}

const userStore = useUserStore()
const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const currentId = ref<number | undefined>(undefined)
const tableData = ref<AiProvider[]>([])
const total = ref(0)

// ==================== 路由配置相关 ====================
const showRoutingPanel = ref(false)
const selectedProvider = ref<AiProvider | null>(null)
const routingLoading = ref(false)
const routingList = ref<AiRoutingInfo[]>([])
const routingDialogVisible = ref(false)
const routingSubmitting = ref(false)
const routingIsEdit = ref(false)
const routingEditId = ref<number | undefined>(undefined)
const routingFormRef = ref<FormInstance>()

const routingForm = reactive<AiRoutingRequest>({
  routingName: '',
  providerId: 0,
  providerCode: '',
  providerName: '',
  simpleModel: '',
  mediumModel: '',
  complexModel: '',
  simpleMaxTokens: 4096,
  mediumMaxTokens: 8192,
  complexMaxTokens: 16384,
  enabled: true,
  remark: ''
})

const routingFormRules: FormRules = {
  routingName: [{ required: true, message: '请输入路由名称', trigger: 'blur' }]
}

const routingDialogTitle = computed(() => routingIsEdit.value ? '编辑路由配置' : '新增路由配置')

const availableModels = computed(() => {
  const prov = selectedProvider.value
  if (!prov || !prov.models || prov.models.length === 0) return []
  return prov.models
})

// 路由面板行高亮
function routingRowClass({ row }: { row: AiProvider }) {
  if (selectedProvider.value && selectedProvider.value.id === row.id) {
    return 'routing-active-row'
  }
  return ''
}

async function openRoutingPanel(row: AiProvider) {
  selectedProvider.value = row
  showRoutingPanel.value = true
  await loadRoutingList()
}

function closeRoutingPanel() {
  showRoutingPanel.value = false
  selectedProvider.value = null
  routingList.value = []
}

async function loadRoutingList() {
  if (!selectedProvider.value) return
  routingLoading.value = true
  try {
    const res = await getRoutingByProvider(selectedProvider.value.id!)
    const data = res?.data
    routingList.value = (data as any)?.records || []
  } catch {
    // ignore
  } finally {
    routingLoading.value = false
  }
}

function resetRoutingForm() {
  routingForm.routingName = ''
  routingForm.providerId = selectedProvider.value?.id || 0
  routingForm.providerCode = selectedProvider.value?.providerCode || ''
  routingForm.providerName = selectedProvider.value?.providerName || ''
  routingForm.simpleModel = ''
  routingForm.mediumModel = ''
  routingForm.complexModel = ''
  routingForm.simpleMaxTokens = 4096
  routingForm.mediumMaxTokens = 8192
  routingForm.complexMaxTokens = 16384
  routingForm.enabled = true
  routingForm.remark = ''
}

function handleRoutingAdd() {
  routingIsEdit.value = false
  routingEditId.value = undefined
  resetRoutingForm()
  routingDialogVisible.value = true
}

function handleRoutingEdit(row: AiRoutingInfo) {
  routingIsEdit.value = true
  routingEditId.value = row.id
  routingForm.routingName = row.routingName
  routingForm.providerId = row.providerId
  routingForm.providerCode = row.providerCode || ''
  routingForm.providerName = row.providerName || ''
  routingForm.simpleModel = row.simpleModel || ''
  routingForm.mediumModel = row.mediumModel || ''
  routingForm.complexModel = row.complexModel || ''
  routingForm.simpleMaxTokens = row.simpleMaxTokens || 4096
  routingForm.mediumMaxTokens = row.mediumMaxTokens || 8192
  routingForm.complexMaxTokens = row.complexMaxTokens || 16384
  routingForm.enabled = row.enabled
  routingForm.remark = row.remark || ''
  routingDialogVisible.value = true
}

async function handleRoutingSubmit() {
  const valid = await routingFormRef.value?.validate().catch(() => false)
  if (!valid) return
  routingSubmitting.value = true
  try {
    if (routingIsEdit.value && routingEditId.value) {
      await updateAiRouting(routingEditId.value, { ...routingForm })
      ElMessage.success('路由更新成功')
    } else {
      await createAiRouting({ ...routingForm })
      ElMessage.success('路由创建成功')
    }
    routingDialogVisible.value = false
    loadRoutingList()
  } catch {
    // ignore
  } finally {
    routingSubmitting.value = false
  }
}

async function handleRoutingDelete(row: AiRoutingInfo) {
  if (!row.id) return
  try {
    await deleteAiRouting(row.id)
    ElMessage.success('删除成功')
    loadRoutingList()
  } catch {
    // ignore
  }
}

async function handleRoutingToggle(row: AiRoutingInfo, enabled: boolean) {
  if (!row.id) return
  try {
    await toggleAiRouting(row.id, enabled)
    ElMessage.success(enabled ? '已启用' : '已禁用')
  } catch {
    row.enabled = !enabled
  }
}

// ==================== 供应商 CRUD ====================

const queryParams = reactive<AiProviderQueryParams>({
  providerName: '',
  providerType: undefined,
  enabled: undefined,
  page: 1,
  pageSize: 10
})

const formRef = ref<FormInstance>()
const form = reactive<FormModel>({
  providerCode: '',
  providerName: '',
  providerType: '',
  baseUrl: '',
  apiKey: '',
  secretKey: '',
  clearSecretKey: false,
  defaultModel: '',
  embeddingModel: '',
  models: [],
  modelsText: '',
  timeoutMs: 30000,
  maxRetries: 3,
  temperature: 0.70,
  enabled: true,
  isPrimary: false,
  priority: 0,
  remark: ''
})

const dialogTitle = computed(() => isEdit.value ? '编辑供应商' : '新增供应商')

const formRules = reactive<FormRules>({
  providerCode: [{ required: true, message: '请输入供应商编码', trigger: 'blur' }],
  providerName: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }],
  providerType: [{ required: true, message: '请选择供应商类型', trigger: 'change' }],
  baseUrl: [{ required: true, message: '请输入 Base URL', trigger: 'blur' }],
  apiKey: [{
    validator: (_rule, value, callback) => {
      if (!isEdit.value && !value) {
        callback(new Error('请输入 API Key'))
      } else {
        callback()
      }
    },
    trigger: 'blur'
  }],
  timeoutMs: [{ required: true, message: '请输入超时时间', trigger: 'change' }],
  maxRetries: [{ required: true, message: '请输入最大重试次数', trigger: 'change' }]
})

async function loadData() {
  loading.value = true
  try {
    const params: AiProviderQueryParams = {
      ...queryParams,
      providerName: queryParams.providerName || undefined
    }
    const res = await getAiProviders(params)
    const data = res?.data
    tableData.value = data?.records || []
    total.value = data?.total || 0
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.page = 1
  loadData()
}

function handleReset() {
  queryParams.providerName = ''
  queryParams.providerType = undefined
  queryParams.enabled = undefined
  queryParams.page = 1
  loadData()
}

function resetForm() {
  form.providerCode = ''
  form.providerName = ''
  form.providerType = ''
  form.baseUrl = ''
  form.apiKey = ''
  form.secretKey = ''
  form.clearSecretKey = false
  form.defaultModel = ''
  form.embeddingModel = ''
  form.models = []
  form.modelsText = ''
  form.timeoutMs = 30000
  form.maxRetries = 3
  form.temperature = 0.70
  form.enabled = true
  form.isPrimary = false
  form.priority = 0
  form.remark = ''
}

function parseModelsText(text: string): string[] {
  return text.split(',')
    .map(s => s.trim())
    .filter(s => s.length > 0)
}

function buildRequest(): AiProviderRequest {
  return {
    providerCode: form.providerCode,
    providerName: form.providerName,
    providerType: form.providerType,
    baseUrl: form.baseUrl,
    apiKey: form.apiKey,
    secretKey: form.secretKey,
    clearSecretKey: form.clearSecretKey,
    defaultModel: form.defaultModel,
    embeddingModel: form.embeddingModel,
    models: parseModelsText(form.modelsText),
    timeoutMs: form.timeoutMs,
    maxRetries: form.maxRetries,
    temperature: form.temperature,
    enabled: form.enabled,
    isPrimary: form.isPrimary,
    priority: form.priority,
    remark: form.remark
  }
}

function handleAdd() {
  isEdit.value = false
  currentId.value = undefined
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: AiProvider) {
  isEdit.value = true
  currentId.value = row.id
  form.providerCode = row.providerCode
  form.providerName = row.providerName
  form.providerType = row.providerType
  form.baseUrl = row.baseUrl
  form.apiKey = ''
  form.secretKey = ''
  form.clearSecretKey = false
  form.defaultModel = row.defaultModel || ''
  form.embeddingModel = row.embeddingModel || ''
  form.models = row.models || []
  form.modelsText = (row.models || []).join(', ')
  form.timeoutMs = row.timeoutMs ?? 30000
  form.maxRetries = row.maxRetries ?? 3
  form.temperature = row.temperature ?? 0.70
  form.enabled = row.enabled
  form.isPrimary = row.isPrimary ?? false
  form.priority = row.priority ?? 0
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      const request = buildRequest()
      if (isEdit.value && currentId.value) {
        await updateAiProvider(currentId.value, request)
        ElMessage.success('更新成功')
      } else {
        await createAiProvider(request)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadData()
    } catch (error) {
      console.error(error)
    } finally {
      submitLoading.value = false
    }
  })
}

function buildUpdateRequest(row: AiProvider, enabled: boolean): AiProviderRequest {
  return {
    providerCode: row.providerCode,
    providerName: row.providerName,
    providerType: row.providerType,
    baseUrl: row.baseUrl,
    apiKey: '',
    secretKey: '',
    clearSecretKey: false,
    defaultModel: row.defaultModel,
    embeddingModel: row.embeddingModel,
    models: row.models,
    timeoutMs: row.timeoutMs,
    maxRetries: row.maxRetries,
    temperature: row.temperature,
    enabled,
    isPrimary: row.isPrimary,
    priority: row.priority,
    remark: row.remark
  }
}

async function handleToggleEnabled(row: AiProvider, enabled: boolean) {
  if (!row.id) return
  try {
    await updateAiProvider(row.id, buildUpdateRequest(row, enabled))
    ElMessage.success(enabled ? '已启用' : '已禁用')
    row.enabled = enabled
  } catch (error) {
    row.enabled = !enabled
    console.error(error)
  }
}

async function handleDelete(row: AiProvider) {
  if (!row.id) return
  try {
    await deleteAiProvider(row.id)
    ElMessage.success('删除成功')
    if (selectedProvider.value?.id === row.id) closeRoutingPanel()
    loadData()
  } catch (error) {
    console.error(error)
  }
}

function formatTestResult(data: ProviderTestResponse): string {
  const status = data.success ? '成功' : '失败'
  const latency = data.latencyMs !== undefined ? `耗时 ${data.latencyMs}ms` : ''
  const httpStatus = data.httpStatus !== undefined ? `HTTP ${data.httpStatus}` : ''
  const models = data.availableModels?.length
    ? `可用模型：${data.availableModels.join(', ')}`
    : ''
  return [`探测${status}`, httpStatus, latency, data.message || '', models]
    .filter(Boolean)
    .join('，')
}

async function handleTest(row: AiProvider) {
  if (!row.id) return
  try {
    const res = await testAiProvider(row.id)
    const data = res?.data
    const message = data ? formatTestResult(data) : '测试完成'
    ElMessageBox.alert(message, '测试结果', { type: data?.success ? 'success' : 'error' })
  } catch (error) {
    console.error(error)
  }
}

function handleSizeChange(val: number) {
  queryParams.pageSize = val
  queryParams.page = 1
  loadData()
}

function handlePageChange(val: number) {
  queryParams.page = val
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<style lang="scss" scoped>
.form-tip {
  margin-left: 120px;
  margin-bottom: 18px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.routing-panel {
  margin-top: 16px;
  border-left: 3px solid var(--el-color-warning);
}

.routing-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.routing-panel-title {
  display: flex;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
}

:deep(.routing-active-row) {
  background-color: var(--el-color-warning-light-9) !important;
}
</style>
