<template>
  <div class="app-container">
    <el-card shadow="never" class="apply-card">
      <template #header>
        <div class="card-header">
          <el-icon><EditPen /></el-icon>
          <span>发起审批申请</span>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="formValues"
        :rules="dynamicRules"
        label-width="110px"
        class="apply-form"
      >
        <el-form-item label="审批表单" prop="_formKey">
          <el-select
            v-model="formValues._formKey"
            placeholder="请选择审批表单"
            style="width: 100%"
            @change="onFormChange"
          >
            <el-option
              v-for="form in formList"
              :key="form.formKey"
              :label="form.formName"
              :value="form.formKey"
            />
          </el-select>
        </el-form-item>

        <template v-if="selectedForm">
          <el-alert
            v-if="selectedForm.applyType === 'SUBSYSTEM_VISIBILITY'"
            type="info"
            :closable="false"
            show-icon
            class="apply-tip"
            title="说明"
            description="提交申请后由系统管理员审批。审批通过后，平台将调用 AI 助手，根据您的身份与申请理由，自动判断应开放哪些业务子系统的可见权限，并即时生效。"
          />

          <div class="dynamic-fields">
            <div
              v-for="field in schemaFields"
              :key="field.field"
              class="field-row"
              :class="{ full: field.type === 'textarea' }"
            >
              <el-form-item
                :label="field.field"
                :prop="field.field"
              >
                <el-input
                  v-if="field.type === 'text'"
                  v-model="formValues[field.field]"
                  :placeholder="field.placeholder || `请输入${field.label}`"
                  :maxlength="field.maxlength || undefined"
                  clearable
                />
                <el-input
                  v-else-if="field.type === 'textarea'"
                  v-model="formValues[field.field]"
                  type="textarea"
                  :rows="4"
                  :placeholder="field.placeholder || `请输入${field.label}`"
                  :maxlength="field.maxlength || undefined"
                  show-word-limit
                />
                <el-input-number
                  v-else-if="field.type === 'number'"
                  v-model="formValues[field.field]"
                  :placeholder="field.placeholder"
                  style="width: 100%"
                />
                <el-select
                  v-else-if="field.type === 'select'"
                  v-model="formValues[field.field]"
                  :placeholder="field.placeholder || `请选择${field.label}`"
                  clearable
                  style="width: 100%"
                >
                  <el-option
                    v-for="opt in field.options"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
                <el-date-picker
                  v-else-if="field.type === 'date'"
                  v-model="formValues[field.field]"
                  type="date"
                  :placeholder="field.placeholder || `请选择${field.label}`"
                  value-format="YYYY-MM-DD"
                  style="width: 100%"
                />
              </el-form-item>
            </div>
          </div>

          <el-form-item label="申请人">
            <el-input :model-value="applicantText" disabled />
          </el-form-item>
        </template>

        <el-form-item v-if="selectedForm">
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            <el-icon><Promotion /></el-icon> 提交申请
          </el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, FormInstance, FormRules } from 'element-plus'
import { EditPen, Promotion } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { getWorkflowForms, submitWorkflowByForm } from '@/api/workflow'
import type { WorkflowForm, FormFieldSchema } from '@/types'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const formList = ref<WorkflowForm[]>([])
const selectedForm = ref<WorkflowForm | null>(null)
const formValues = reactive<Record<string, any>>({
  _formKey: ''
})

const applicantText = computed(() => {
  const u = userStore.userInfo
  return u ? `${u.nickname || u.username}（${u.username}）` : '-'
})

const schemaFields = computed((): FormFieldSchema[] => {
  if (!selectedForm.value?.schemaJson) return []
  try {
    const parsed = JSON.parse(selectedForm.value.schemaJson)
    // 兼容两种存储格式：直接数组，或 { fields: [...] }
    if (Array.isArray(parsed)) return parsed
    if (parsed && Array.isArray(parsed.fields)) return parsed.fields
    return []
  } catch {
    return []
  }
})

const dynamicRules = computed((): FormRules => {
  const rules: FormRules = {
    _formKey: [{ required: true, message: '请选择审批表单', trigger: 'change' }]
  }
  schemaFields.value.forEach((field) => {
    if (field.required) {
      rules[field.field] = [{ required: true, message: `请填写${field.field}`, trigger: 'blur' }]
    }
  })
  return rules
})

watch(schemaFields, (fields) => {
  const formKey = formValues._formKey
  Object.keys(formValues).forEach((k) => delete formValues[k])
  formValues._formKey = formKey
  fields.forEach((f) => {
    formValues[f.field] = f.type === 'number' ? undefined : ''
  })
})

function onFormChange(key: string) {
  selectedForm.value = formList.value.find((f) => f.formKey === key) || null
  nextTick(() => {
    formRef.value?.clearValidate('_formKey')
  })
}

watch(
  () => formValues._formKey,
  (key) => {
    onFormChange(key)
  }
)

async function loadForms() {
  try {
    const res = await getWorkflowForms({ status: 1 })
    formList.value = res.data || []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载表单失败')
  }
}

function handleSubmit() {
  formRef.value?.validate(async (valid, invalidFields) => {
    if (!valid || !selectedForm.value) {
      if (invalidFields && Object.keys(invalidFields).length > 0) {
        ElMessage.warning('请完善表单信息')
      }
      return
    }
    submitting.value = true
    try {
      const u = userStore.userInfo
      const values = { ...formValues }
      delete values._formKey
      // 子系统可见场景补充当前用户信息（目标用户即申请人）
      if (selectedForm.value.applyType === 'SUBSYSTEM_VISIBILITY') {
        values.targetUserId = u?.id
        values.targetUsername = u?.username
        values.type = 'SUBSYSTEM_VISIBILITY'
        // 将表单中的“理由/原因/说明”字段统一映射为后端期望的 remark
        const reasonKey = Object.keys(values).find((k) =>
          /(理由|原因|说明|reason|remark|description)/i.test(k)
        )
        if (reasonKey) {
          values.remark = values[reasonKey]
        } else {
          ElMessage.warning('未检测到申请理由字段，请检查表单配置')
        }
      }
      await submitWorkflowByForm({
        formKey: selectedForm.value.formKey,
        title: selectedForm.value.formName,
        applyContent: JSON.stringify(values),
        applicant: u?.username
      })
      ElMessage.success('申请已提交，等待审批')
      router.push('/workflow/list')
    } catch (e: any) {
      ElMessage.error(e?.message || '提交失败')
    } finally {
      submitting.value = false
    }
  })
}

loadForms()
</script>

<style scoped>
.apply-card {
  max-width: 900px;
  margin: 0 auto;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
.apply-tip {
  margin-bottom: 20px;
}
.apply-form {
  margin-top: 8px;
}
.dynamic-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 0 16px;
}
.field-row {
  width: calc(50% - 8px);
}
.field-row.full {
  width: 100%;
}
.field-row :deep(.el-form-item) {
  width: 100%;
}
</style>
