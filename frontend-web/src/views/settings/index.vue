<template>
  <div class="app-container">
    <!-- 界面设置 -->
    <el-card shadow="never" class="settings-card">
      <template #header>
        <div class="card-header">
          <el-icon><Monitor /></el-icon>
          <span>界面设置</span>
        </div>
      </template>
      <div class="setting-item">
        <div class="setting-label">
          <span>显示智能助手</span>
          <span class="setting-desc">右下角悬浮的 AI 助手图标</span>
        </div>
        <el-switch v-model="showAiAssistant" @change="handleAiAssistantChange" />
      </div>
    </el-card>

    <!-- 版本说明 -->
    <el-card shadow="never" class="settings-card">
      <template #header>
        <div class="card-header">
          <el-icon><InfoFilled /></el-icon>
          <span>版本说明</span>
        </div>
      </template>
      <div v-if="versionInfo" class="version-content">
        <div class="current-version">
          <el-tag type="primary" size="large" effect="dark">
            当前版本: {{ versionInfo.version }}
          </el-tag>
          <span class="build-time">构建时间: {{ versionInfo.buildTime }}</span>
        </div>

        <el-timeline class="changelog">
          <el-timeline-item
            v-for="(log, idx) in versionInfo.changelog"
            :key="idx"
            :timestamp="log.date"
            placement="top"
            :color="idx === 0 ? '#409eff' : '#c0c4cc'"
          >
            <el-card shadow="never" class="changelog-card">
              <h4>{{ log.version }}</h4>
              <ul class="change-list">
                <li v-for="(change, ci) in log.changes" :key="ci">
                  <el-icon><Check /></el-icon>
                  {{ change }}
                </li>
              </ul>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </div>
      <el-skeleton v-else :rows="5" animated />
    </el-card>

    <!-- 意见反馈 -->
    <el-card shadow="never" class="settings-card">
      <template #header>
        <div class="card-header">
          <el-icon><ChatLineRound /></el-icon>
          <span>意见反馈</span>
        </div>
      </template>

      <el-tabs v-model="feedbackTab">
        <!-- 提交反馈 -->
        <el-tab-pane label="提交反馈" name="submit">
          <el-form ref="feedbackFormRef" :model="feedbackForm" :rules="fbRules" label-width="80px" size="default" class="feedback-form">
            <el-form-item label="反馈类型" prop="type">
              <el-radio-group v-model="feedbackForm.type">
                <el-radio value="suggestion">功能建议</el-radio>
                <el-radio value="bug">缺陷报告</el-radio>
                <el-radio value="other">其他</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="反馈内容" prop="content">
              <el-input
                v-model="feedbackForm.content"
                type="textarea"
                :rows="4"
                placeholder="请详细描述您遇到的问题或建议..."
                maxlength="2000"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="联系方式">
              <el-input v-model="feedbackForm.contact" placeholder="邮箱或手机号，方便我们联系您（选填）" maxlength="50" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="submitting" @click="handleSubmitFeedback">
                提交反馈
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 我的反馈 -->
        <el-tab-pane label="我的反馈" name="history">
          <div v-if="myFeedbacks.length === 0 && !loadingFeedback" class="empty-feedback">
            <el-empty description="暂无反馈记录" :image-size="100" />
          </div>
          <div v-else v-loading="loadingFeedback">
            <div v-for="fb in myFeedbacks" :key="fb.id" class="feedback-item">
              <div class="feedback-header">
                <el-tag
                  :type="fb.type === 'bug' ? 'danger' : fb.type === 'suggestion' ? 'success' : 'info'"
                  size="small"
                >
                  {{ fb.type === 'bug' ? '缺陷' : fb.type === 'suggestion' ? '建议' : '其他' }}
                </el-tag>
                <el-tag
                  :type="fb.status === 'resolved' ? 'success' : 'warning'"
                  size="small"
                  effect="plain"
                >
                  {{ fb.status === 'resolved' ? '已处理' : '处理中' }}
                </el-tag>
                <span class="feedback-time">{{ fb.createTime }}</span>
              </div>
              <p class="feedback-content">{{ fb.content }}</p>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 技术栈信息 -->
    <el-card shadow="never" class="settings-card">
      <template #header>
        <div class="card-header">
          <el-icon><Monitor /></el-icon>
          <span>关于系统</span>
        </div>
      </template>
      <div class="about-info">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="系统名称">统一授权中台</el-descriptions-item>
          <el-descriptions-item label="技术架构">Spring Boot 3 + Vue 3 + Element Plus</el-descriptions-item>
          <el-descriptions-item label="认证协议">OAuth 2.0 / OpenID Connect 1.0</el-descriptions-item>
          <el-descriptions-item label="授权服务器">Spring Authorization Server</el-descriptions-item>
          <el-descriptions-item label="API 网关">Spring Cloud Gateway</el-descriptions-item>
          <el-descriptions-item label="数据库">MySQL 8.0</el-descriptions-item>
          <el-descriptions-item label="缓存">Redis</el-descriptions-item>
          <el-descriptions-item label="注册中心">Nacos</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { InfoFilled, ChatLineRound, Check, Monitor } from '@element-plus/icons-vue'
import { getVersionInfo, submitFeedback, getMyFeedback } from '@/api/profile'
import type { VersionInfo, FeedbackRecord } from '@/types'

const AI_ASSISTANT_HIDDEN_KEY = 'ai_assistant_hidden'

const feedbackTab = ref('submit')
const showAiAssistant = ref(true)

onMounted(() => {
  showAiAssistant.value = localStorage.getItem(AI_ASSISTANT_HIDDEN_KEY) !== 'true'
})

function handleAiAssistantChange(val: boolean | string | number) {
  const visible = !!val
  localStorage.setItem(AI_ASSISTANT_HIDDEN_KEY, visible ? 'false' : 'true')
  window.dispatchEvent(new CustomEvent('ai-assistant-visibility-change', { detail: { visible } }))
  ElMessage.success(visible ? '智能助手已恢复显示' : '智能助手已隐藏')
}

const feedbackFormRef = ref<FormInstance>()
const submitting = ref(false)
const loadingFeedback = ref(false)

const versionInfo = ref<VersionInfo | null>(null)
const myFeedbacks = ref<FeedbackRecord[]>([])

const feedbackForm = reactive({
  type: 'suggestion' as string,
  content: '',
  contact: '',
})

const fbRules: FormRules = {
  content: [
    { required: true, message: '请输入反馈内容', trigger: 'blur' },
    { min: 5, message: '反馈内容至少5个字符', trigger: 'blur' },
  ],
}

onMounted(async () => {
  try {
    const res = await getVersionInfo()
    if (res?.data) versionInfo.value = res.data
  } catch { /* */ }
})

async function handleSubmitFeedback() {
  if (!feedbackFormRef.value) return
  await feedbackFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await submitFeedback({
        content: feedbackForm.content,
        contact: feedbackForm.contact || undefined,
        type: feedbackForm.type,
      })
      ElMessage.success('感谢您的反馈！')
      feedbackForm.content = ''
      feedbackForm.contact = ''
    } catch { /* */ } finally {
      submitting.value = false
    }
  })
}

async function loadFeedback() {
  loadingFeedback.value = true
  try {
    const res = await getMyFeedback()
    if (res?.data) myFeedbacks.value = res.data
  } catch { /* */ } finally {
    loadingFeedback.value = false
  }
}

// 切换 tab 时加载反馈历史
import { watch } from 'vue'
watch(feedbackTab, (val) => {
  if (val === 'history' && myFeedbacks.value.length === 0) {
    loadFeedback()
  }
})
</script>

<style scoped lang="scss">
.settings-card {
  margin-bottom: 16px;
}

.setting-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;

  .setting-label {
    display: flex;
    flex-direction: column;
    gap: 4px;

    span {
      font-size: 14px;
      color: #303133;
    }

    .setting-desc {
      font-size: 12px;
      color: #909399;
    }
  }
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}

.version-content {
  padding: 4px 0;
}

.current-version {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.build-time {
  font-size: 13px;
  color: #909399;
}

.changelog {
  padding-left: 8px;
}

.changelog-card {
  h4 {
    margin: 0 0 8px;
    font-size: 15px;
    color: #303133;
  }
}

.change-list {
  list-style: none;
  padding: 0;
  margin: 0;

  li {
    display: flex;
    align-items: flex-start;
    gap: 6px;
    padding: 3px 0;
    font-size: 13px;
    color: #606266;
    line-height: 1.6;

    .el-icon {
      color: #67c23a;
      margin-top: 2px;
      flex-shrink: 0;
    }
  }
}

.feedback-form {
  max-width: 600px;
  padding-top: 8px;
}

.empty-feedback {
  padding: 32px 0;
}

.feedback-item {
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;

  &:last-child {
    border-bottom: none;
  }
}

.feedback-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.feedback-time {
  font-size: 12px;
  color: #c0c4cc;
  margin-left: auto;
}

.feedback-content {
  margin: 0;
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
}

.about-info {
  padding-top: 4px;
}
</style>
