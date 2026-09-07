<template>
  <div class="app-container">
    <el-row :gutter="16">
      <!-- 发送表单 -->
      <el-col :span="16">
        <el-card shadow="never">
          <template #header><div class="card-header"><span>发送消息</span></div></template>

          <el-form :model="form" ref="formRef" :rules="rules" label-width="100px">
            <el-form-item label="发送方式">
              <el-radio-group v-model="sendMode">
                <el-radio-button label="single">单发</el-radio-button>
                <el-radio-button label="broadcast">广播</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="消息模板">
              <el-select
                v-model="selectedTemplateCode"
                placeholder="请选择消息模板（可选）"
                clearable
                style="width: 100%"
                @change="handleTemplateChange"
              >
                <el-option
                  v-for="t in enabledTemplates"
                  :key="t.templateCode"
                  :label="t.templateName"
                  :value="t.templateCode"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="消息类型" prop="messageType">
              <el-select v-model="form.messageType" style="width: 100%">
                <el-option label="系统公告" value="SYSTEM_NOTICE" />
                <el-option label="事件推送" value="EVENT_PUSH" />
                <el-option label="审批通知" value="APPROVAL_NOTIFY" />
                <el-option label="子系统通信" value="SUBSYSTEM_COMM" />
                <el-option label="用户消息" value="USER_MESSAGE" />
              </el-select>
            </el-form-item>

            <el-form-item label="标题" prop="title">
              <el-input v-model="form.title" :disabled="!!selectedTemplateCode" />
            </el-form-item>

            <el-form-item label="内容" prop="content">
              <el-input v-model="form.content" type="textarea" :rows="5" :disabled="!!selectedTemplateCode" />
            </el-form-item>

            <!-- 模板变量 -->
            <template v-if="templateVars.length > 0">
              <el-form-item
                v-for="v in templateVars"
                :key="v.name"
                :label="v.desc || v.name"
              >
                <el-input v-model="templateVarValues[v.name]" placeholder="请输入变量值" />
              </el-form-item>
            </template>

            <el-form-item label="发送渠道">
              <el-checkbox-group v-model="form.channels">
                <el-checkbox v-for="opt in availableChannels" :key="opt.value" :label="opt.value">{{ opt.label }}</el-checkbox>
              </el-checkbox-group>
            </el-form-item>

            <template v-if="sendMode === 'single'">
              <el-form-item label="接收人" prop="receivers">
                <UserSelect v-model="form.receivers" placeholder="请选择接收人" />
              </el-form-item>
            </template>
            <template v-else>
              <el-form-item label="目标子系统" prop="targetSubsystems">
                <el-select
                  v-model="form.targetSubsystems"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  :loading="loadingSubsystems"
                  style="width: 100%"
                  placeholder="请选择或输入子系统标识"
                >
                  <el-option
                    v-for="opt in subsystemOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </el-form-item>
            </template>

            <el-form-item label="业务ID">
              <el-input v-model="form.businessId" placeholder="可选，关联业务ID" />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="handleSend" :loading="sending">发送</el-button>
              <el-button @click="resetForm">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 模板预览 -->
      <el-col :span="8" v-if="selectedTemplate">
        <el-card shadow="never">
          <template #header><div class="card-header"><span>模板预览</span></div></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="模板编码">{{ selectedTemplate.templateCode }}</el-descriptions-item>
            <el-descriptions-item label="模板名称">{{ selectedTemplate.templateName }}</el-descriptions-item>
            <el-descriptions-item label="默认渠道">{{ channelLabel(selectedTemplate.channel) }}</el-descriptions-item>
            <el-descriptions-item label="标题模板">{{ selectedTemplate.titleTemplate }}</el-descriptions-item>
            <el-descriptions-item label="内容模板">
              <div style="white-space: pre-wrap">{{ selectedTemplate.contentTemplate }}</div>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { sendMessage, broadcastMessage, getMessageTemplates } from '@/api/message'
import { getDictDataByKey } from '@/api/system'
import type { DictDataInfo, MessageSendRequest, MessageBroadcastRequest, MessageTemplateVO } from '@/types'
import { channelOptions, channelLabel } from '@/utils/channel'
import UserSelect from '@/components/UserSelect.vue'

interface TemplateVarDef {
  name: string
  desc?: string
}

const formRef = ref<FormInstance>()
const sending = ref(false)
const sendMode = ref<'single' | 'broadcast'>('single')
const templates = ref<MessageTemplateVO[]>([])
const selectedTemplateCode = ref<string>('')
const templateVars = ref<TemplateVarDef[]>([])
const templateVarValues = reactive<Record<string, string>>({})

const form = reactive({
  messageType: 'SYSTEM_NOTICE',
  title: '',
  content: '',
  channels: ['IN_APP'] as string[],
  receivers: [] as string[],
  targetSubsystems: [] as string[],
  businessId: '',
  templateCode: undefined as string | undefined,
  templateVars: undefined as Record<string, string> | undefined
})

const subsystemOptions = ref<{ label: string; value: string }[]>([])
const loadingSubsystems = ref(false)

// MQ 渠道只在广播模式下可用
const availableChannels = computed(() => {
  if (sendMode.value === 'broadcast') return channelOptions
  return channelOptions.filter(c => c.value !== 'MQ')
})

const rules: FormRules = {
  messageType: [{ required: true, message: '请选择消息类型', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
}

const selectedTemplate = computed(() =>
  templates.value.find(t => t.templateCode === selectedTemplateCode.value)
)

const enabledTemplates = computed(() =>
  templates.value.filter(t => t.status === undefined || t.status === 1)
)

onMounted(async () => {
  try {
    loadingSubsystems.value = true
    const [templateRes, subsystemRes] = await Promise.all([
      getMessageTemplates({ page: 1, size: 999, status: 1 }),
      // TODO: 根据实际字典类型调整 key，如 'subsystems' / 'message_subsystem'
      getDictDataByKey('subsystem').catch(() => ({ data: [] }))
    ])
    templates.value = templateRes?.data?.list || []
    subsystemOptions.value = (subsystemRes?.data || []).map((d: DictDataInfo) => ({
      label: d.dictLabel,
      value: d.dictValue
    }))
  } catch {
    ElMessage.error('模板列表加载失败')
  } finally {
    loadingSubsystems.value = false
  }
})

function handleTemplateChange(code: string) {
  form.templateCode = code || undefined
  templateVars.value = []
  Object.keys(templateVarValues).forEach(k => delete templateVarValues[k])

  if (!code) {
    form.title = ''
    form.content = ''
    form.channels = ['IN_APP']
    return
  }

  const t = templates.value.find(item => item.templateCode === code)
  if (!t) return

  // 解析模板变量（优先使用模板配置的 variables，否则从标题/内容中自动提取 {{var}}）
  if (t.variables) {
    try {
      const vars = JSON.parse(t.variables) as TemplateVarDef[]
      templateVars.value = Array.isArray(vars) ? vars : []
    } catch {
      templateVars.value = extractTemplateVars(t.titleTemplate, t.contentTemplate)
    }
  } else {
    templateVars.value = extractTemplateVars(t.titleTemplate, t.contentTemplate)
  }
  templateVars.value.forEach(v => { templateVarValues[v.name] = '' })

  // 默认渠道取模板渠道
  if (t.channel) {
    form.channels = [t.channel]
  }

  renderTemplate()
}

function renderTemplate() {
  const t = selectedTemplate.value
  if (!t) return

  let title = t.titleTemplate || ''
  let content = t.contentTemplate || ''
  templateVars.value.forEach(v => {
    const val = templateVarValues[v.name] || ''
    // 兼容 {{name}} 和 {{ name }} 写法
    const regex = new RegExp(`{{\\s*${v.name}\\s*}}`, 'g')
    title = title.replace(regex, val)
    content = content.replace(regex, val)
  })

  form.title = title
  form.content = content
  // 只作为前端状态保留，后端不需要再处理
  form.templateVars = { ...templateVarValues }
}

function extractTemplateVars(titleTemplate?: string, contentTemplate?: string): TemplateVarDef[] {
  const text = `${titleTemplate || ''}${contentTemplate || ''}`
  const regex = /\{\{\s*(\w+)\s*\}\}/g
  const names = new Set<string>()
  let match: RegExpExecArray | null
  while ((match = regex.exec(text)) !== null) {
    names.add(match[1])
  }
  return Array.from(names).map(name => ({ name }))
}

watch(sendMode, (mode) => {
  // 切换到单发时，自动去掉 MQ 渠道
  if (mode === 'single') {
    form.channels = form.channels.filter(c => c !== 'MQ')
  }
})

watch(templateVarValues, () => {
  if (selectedTemplateCode.value) {
    renderTemplate()
  }
}, { deep: true })

async function handleSend() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    if (sendMode.value === 'single' && (!form.receivers || form.receivers.length === 0)) {
      ElMessage.warning('请选择接收人')
      return
    }
    if (sendMode.value === 'broadcast' && (!form.targetSubsystems || form.targetSubsystems.length === 0)) {
      ElMessage.warning('请输入目标子系统')
      return
    }

    sending.value = true
    try {
      if (sendMode.value === 'single') {
        const payload: MessageSendRequest = {
          messageType: form.messageType,
          title: form.title,
          content: form.content,
          channels: form.channels,
          receivers: form.receivers,
          businessId: form.businessId || undefined,
          templateCode: form.templateCode,
          templateVars: form.templateCode ? { ...templateVarValues } : undefined
        }
        await sendMessage(payload)
      } else {
        const payload: MessageBroadcastRequest = {
          messageType: form.messageType,
          title: form.title,
          content: form.content,
          targetSubsystems: form.targetSubsystems,
          channels: form.channels,
          businessId: form.businessId || undefined,
          templateCode: form.templateCode,
          templateVars: form.templateCode ? { ...templateVarValues } : undefined
        }
        await broadcastMessage(payload)
      }
      ElMessage.success('消息发送成功')
      resetForm()
    } catch { /* */ }
    finally { sending.value = false }
  })
}

function resetForm() {
  formRef.value?.resetFields()
  selectedTemplateCode.value = ''
  form.templateCode = undefined
  form.templateVars = undefined
  templateVars.value = []
  Object.keys(templateVarValues).forEach(k => delete templateVarValues[k])
  form.receivers = []
  form.targetSubsystems = []
  form.businessId = ''
  form.channels = ['IN_APP']
}
</script>

<style scoped>
.card-header { font-weight: 600; }
</style>
