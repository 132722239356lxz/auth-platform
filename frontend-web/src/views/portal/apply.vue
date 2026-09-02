<template>
  <div class="portal-page">
    <div class="page-header">
      <el-page-header title="返回" content="发起审批申请" @back="goBack" />
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
            <div class="nav-item active" @click="stay">
              <el-icon><DocumentChecked /></el-icon>
              <span>发起审批申请</span>
            </div>
            <div class="nav-item" @click="goRecords">
              <el-icon><Document /></el-icon>
              <span>我的申请记录</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="16">
        <el-card shadow="hover" class="apply-card">
          <template #header>
            <div class="card-header">
              <span>填写申请信息</span>
            </div>
          </template>

          <el-form
            ref="formRef"
            :model="formValues"
            :rules="dynamicRules"
            label-width="100px"
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

            <transition name="form-fade">
              <div v-if="selectedForm" class="dynamic-fields">
                <el-alert
                  v-if="selectedForm.applyType === 'SUBSYSTEM_VISIBILITY'"
                  type="info"
                  :closable="false"
                  show-icon
                  class="apply-tip"
                >
                  <template #title>
                    <span>说明</span>
                  </template>
                  提交申请后由系统管理员审批。审批通过后，平台将调用 AI
                  助手，根据您的身份与申请理由，自动判断应开放哪些业务子系统的可见权限，并即时生效。
                </el-alert>

                <div
                  v-for="field in schemaFields"
                  :key="field.field"
                  class="field-row"
                >
                  <el-form-item :label="field.label" :prop="field.field">
                    <el-input
                      v-if="field.type === 'text'"
                      v-model="formValues[field.field]"
                      :placeholder="field.placeholder || `请输入${field.label}`"
                      :maxlength="field.maxlength"
                      show-word-limit
                    />
                    <el-input
                      v-else-if="field.type === 'textarea'"
                      v-model="formValues[field.field]"
                      type="textarea"
                      :rows="4"
                      :placeholder="field.placeholder || `请输入${field.label}`"
                      :maxlength="field.maxlength"
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
                      value-format="YYYY-MM-DD"
                      :placeholder="field.placeholder || `请选择${field.label}`"
                      style="width: 100%"
                    />
                  </el-form-item>
                </div>

                <el-form-item label="申请人">
                  <el-input :model-value="applicantText" disabled />
                </el-form-item>

                <el-form-item class="form-actions">
                  <el-button type="primary" :loading="submitting" @click="handleSubmit">
                    <el-icon><Promotion /></el-icon> 提交申请
                  </el-button>
                  <el-button @click="goBack">
                    <el-icon><ArrowLeft /></el-icon> 返回
                  </el-button>
                </el-form-item>
              </div>
            </transition>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, FormInstance, FormRules } from 'element-plus'
import {
  UserFilled,
  DocumentChecked,
  Document,
  Promotion,
  ArrowLeft
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { getPublicFormOptions, submitWorkflowByForm } from '@/api/workflow'
import type { WorkflowForm, FormFieldSchema } from '@/types'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const formList = ref<WorkflowForm[]>([])
const selectedForm = ref<WorkflowForm | null>(null)
const formValues = reactive<Record<string, any>>({ _formKey: '' })

const avatarUrl = computed(() => userStore.userInfo?.avatar || '')

const applicantText = computed(() => {
  const u = userStore.userInfo
  return u ? `${u.nickname || u.username}（${u.username}）` : '-'
})

function parseFieldsFromSchema(schemaJson?: string): FormFieldSchema[] {
  if (!schemaJson) return []
  try {
    const parsed = JSON.parse(schemaJson)
    if (Array.isArray(parsed)) return parsed
    if (parsed && Array.isArray(parsed.fields)) return parsed.fields
    return []
  } catch {
    return []
  }
}

const schemaFields = computed((): FormFieldSchema[] => {
  if (!selectedForm.value?.schemaJson) return []
  return parseFieldsFromSchema(selectedForm.value.schemaJson)
})

const dynamicRules = computed((): FormRules => {
  const rules: FormRules = {
    _formKey: [{ required: true, message: '请选择审批表单', trigger: 'change' }]
  }
  schemaFields.value.forEach((field) => {
    if (field.required) {
      rules[field.field] = [{ required: true, message: `请填写${field.label}`, trigger: 'blur' }]
    }
  })
  return rules
})

watch(
  schemaFields,
  (fields) => {
    const currentKey = formValues._formKey
    Object.keys(formValues).forEach((k) => delete formValues[k])
    formValues._formKey = currentKey
    fields.forEach((f) => {
      formValues[f.field] = f.type === 'number' ? undefined : ''
    })
  },
  { immediate: false }
)

function onFormChange(key: string) {
  selectedForm.value = formList.value.find((f) => f.formKey === key) || null
  nextTick(() => {
    formRef.value?.clearValidate('_formKey')
  })
}

async function loadForms() {
  try {
    const res = await getPublicFormOptions()
    formList.value = res.data || []
    if (!formList.value.length) {
      ElMessage.warning('暂无可用的审批表单')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载表单失败')
  }
}

function stay() {
  // 当前页已是发起申请
}

function goBack() {
  router.back()
}

function goRecords() {
  router.push('/portal/applications')
}

async function handleSubmit() {
  formRef.value?.validate(async (valid, invalidFields) => {
    if (!valid || !selectedForm.value) {
      if (invalidFields) {
        const first = Object.values(invalidFields)[0]?.[0]
        if (first?.message) ElMessage.warning(first.message)
        else ElMessage.warning('请完善表单信息')
      }
      return
    }
    submitting.value = true
    try {
      const u = userStore.userInfo
      const values: Record<string, any> = {}
      Object.keys(formValues).forEach((k) => {
        if (k !== '_formKey') values[k] = formValues[k]
      })

      if (selectedForm.value.applyType === 'SUBSYSTEM_VISIBILITY') {
        values.targetUserId = u?.id
        values.targetUsername = u?.username
        values.type = 'SUBSYSTEM_VISIBILITY'
        const reasonKey = Object.keys(values).find((k) =>
          /(理由|原因|说明|reason|remark|description)/i.test(k)
        )
        if (reasonKey) {
          values.remark = values[reasonKey]
        } else {
          ElMessage.warning('未检测到申请理由字段，请检查表单配置')
          return
        }
      }

      await submitWorkflowByForm({
        formKey: selectedForm.value.formKey,
        title: selectedForm.value.formName,
        applyContent: JSON.stringify(values),
        applicant: u?.username
      })
      ElMessage.success('申请已提交，等待审批')
      router.push('/portal/applications')
    } catch (e: any) {
      ElMessage.error(e?.message || '提交失败')
    } finally {
      submitting.value = false
    }
  })
}

loadForms()
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

    :deep(.avatar) {
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

.apply-card {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-weight: 600;
  }
}

.apply-form {
  margin-top: 4px;
}

.apply-tip {
  margin-bottom: 20px;
  border-radius: 8px;
}

.dynamic-fields {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field-row {
  transition: background 0.2s;
  border-radius: 8px;
  padding: 4px 8px;

  &:hover {
    background: var(--el-fill-color-light);
  }
}

.form-actions {
  margin-top: 12px;
  padding-top: 16px;
  border-top: 1px dashed var(--el-border-color);

  :deep(.el-form-item__content) {
    gap: 12px;
    flex-wrap: wrap;
  }
}

.form-fade-enter-active,
.form-fade-leave-active {
  transition: all 0.3s ease;
}

.form-fade-enter-from,
.form-fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
