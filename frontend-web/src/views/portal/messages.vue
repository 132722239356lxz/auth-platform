<template>
  <div class="portal-page">
    <div class="page-header">
      <el-page-header @back="router.back()" title="返回" content="站内信" />
    </div>

    <el-row :gutter="20">
      <!-- 左侧：发送站内信 --> 
      <el-col :xs="24" :md="8">
        <el-card shadow="hover" class="message-card">
          <template #header>
            <div class="card-header"><span>发送站内信</span></div>
          </template>
          <el-form :model="sendForm" ref="sendFormRef" :rules="sendRules" label-position="top">
            <el-form-item label="接收人" prop="receivers">
              <el-select
                v-model="sendForm.receivers"
                multiple
                filterable
                allow-create
                default-first-option
                placeholder="输入用户名后按回车"
                style="width: 100%"
                :loading="loadingUsers"
              >
                <el-option
                  v-for="u in userOptions"
                  :key="u.value"
                  :label="u.label"
                  :value="u.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="标题" prop="title">
              <el-input v-model="sendForm.title" placeholder="请输入标题" maxlength="100" show-word-limit />
            </el-form-item>
            <el-form-item label="内容" prop="content">
              <el-input
                v-model="sendForm.content"
                type="textarea"
                :rows="5"
                placeholder="请输入站内信内容"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSend" :loading="sending" style="width: 100%">发送</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 右侧：收件箱 -->
      <el-col :xs="24" :md="16">
        <el-card shadow="hover" class="message-card">
          <template #header>
            <div class="card-header">
              <span>收件箱</span>
              <el-button text size="small" @click="loadInbox">
                <el-icon><Refresh /></el-icon>刷新
              </el-button>
            </div>
          </template>

          <div v-loading="loadingInbox">
            <el-empty v-if="messages.length === 0" description="暂无站内信" :image-size="80" />
            <div v-else class="message-list">
              <div
                v-for="msg in messages"
                :key="msg.messageId"
                class="message-item"
                :class="{ unread: !msg.isRead }"
                @click="viewMessage(msg)"
              >
                <div class="message-main">
                  <div class="message-title-row">
                    <span class="message-title" :class="{ unread: !msg.isRead }">{{ msg.title }}</span>
                    <el-tag v-if="!msg.isRead" size="small" type="danger">未读</el-tag>
                  </div>
                  <div class="message-meta">
                    <span>发送者：{{ msg.sender || '-' }}</span>
                    <span>{{ formatTime(msg.createTime) }}</span>
                  </div>
                  <div class="message-preview">{{ msg.content }}</div>
                </div>
                <el-button
                  v-if="!msg.isRead"
                  link
                  type="primary"
                  size="small"
                  @click.stop="markRead(msg)"
                >标记已读</el-button>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 消息详情弹窗 -->
    <el-dialog v-model="detailVisible" title="消息详情" width="520px">
      <div v-if="currentMessage" class="message-detail">
        <h4>{{ currentMessage.title }}</h4>
        <div class="detail-meta">
          <span>发送者：{{ currentMessage.sender || '-' }}</span>
          <span>{{ formatTime(currentMessage.createTime) }}</span>
        </div>
        <div class="detail-content">{{ currentMessage.content }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { sendPortalMessage, getPortalInbox, markPortalMessageRead } from '@/api/message'
import { getUsers } from '@/api/system'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import dayjs from 'dayjs'
import type { MessageInfo } from '@/types'

const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

const sendFormRef = ref<FormInstance>()
const sending = ref(false)
const loadingInbox = ref(false)
const loadingUsers = ref(false)

const messages = ref<MessageInfo[]>([])
const userOptions = ref<{ label: string; value: string }[]>([])

const sendForm = reactive({
  receivers: [] as string[],
  title: '',
  content: ''
})

const sendRules: FormRules = {
  receivers: [{ required: true, message: '请选择或输入接收人', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
}

const detailVisible = ref(false)
const currentMessage = ref<MessageInfo | null>(null)

onMounted(() => {
  loadInbox()
  loadUsers()
})

async function loadUsers() {
  loadingUsers.value = true
  try {
    const res = await getUsers()
    if (res.code === 200 && res.data) {
      userOptions.value = res.data
        .filter((u: any) => u.username !== userStore.username)
        .map((u: any) => ({ label: `${u.nickname || u.username} (${u.username})`, value: u.username }))
    }
  } catch { /* ignore */ } finally {
    loadingUsers.value = false
  }
}

async function loadInbox() {
  loadingInbox.value = true
  try {
    const res = await getPortalInbox(50)
    messages.value = res.data || []
  } catch (error: any) {
    ElMessage.error(error?.message || '加载收件箱失败')
  } finally {
    loadingInbox.value = false
  }
}

async function handleSend() {
  if (!sendFormRef.value) return
  await sendFormRef.value.validate(async (valid) => {
    if (!valid) return
    sending.value = true
    try {
      const res = await sendPortalMessage({
        title: sendForm.title,
        content: sendForm.content,
        receivers: sendForm.receivers
      })
      if (res.code === 200) {
        ElMessage.success('站内信发送成功')
        sendForm.receivers = []
        sendForm.title = ''
        sendForm.content = ''
        // 刷新收件箱与顶部铃铛未读数
        loadInbox()
        if (userStore.username) {
          appStore.loadUnreadCount(userStore.username)
        }
      } else {
        ElMessage.error(res.msg || '发送失败')
      }
    } catch (error: any) {
      ElMessage.error(error?.message || '发送失败')
    } finally {
      sending.value = false
    }
  })
}

function viewMessage(msg: MessageInfo) {
  currentMessage.value = msg
  detailVisible.value = true
  if (!msg.isRead) {
    markRead(msg)
  }
}

async function markRead(msg: MessageInfo) {
  if (!msg.messageId) return
  try {
    await markPortalMessageRead(msg.messageId)
    msg.isRead = true
  } catch { /* ignore */ }
}

function formatTime(time?: string) {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm') : '-'
}
</script>

<style scoped lang="scss">
.portal-page {
  max-width: 1000px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 20px;
}

.message-card {
  margin-bottom: 20px;

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-weight: 600;
  }
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border-radius: 10px;
  background: #f7f8fa;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: #eef2f8;
  }

  &.unread {
    border-left: 3px solid #409eff;
  }

  .message-main {
    flex: 1;
    min-width: 0;
  }

  .message-title-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 6px;
  }

  .message-title {
    font-weight: 500;
    color: #303133;

    &.unread {
      font-weight: 700;
    }
  }

  .message-meta {
    display: flex;
    gap: 16px;
    font-size: 12px;
    color: #909399;
    margin-bottom: 6px;
  }

  .message-preview {
    font-size: 13px;
    color: #606266;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.message-detail {
  h4 {
    margin: 0 0 12px;
    font-size: 16px;
  }

  .detail-meta {
    display: flex;
    gap: 16px;
    font-size: 12px;
    color: #909399;
    margin-bottom: 16px;
  }

  .detail-content {
    font-size: 14px;
    line-height: 1.8;
    color: #303133;
    white-space: pre-wrap;
  }
}
</style>
