<template>
  <div class="app-container chat-layout">
    <!-- 左侧：历史会话列表 -->
    <div class="chat-sessions" :class="{ collapsed: sessionsCollapsed }">
      <div class="sessions-header">
        <span v-if="!sessionsCollapsed">{{ $t('ai.chat.sessionList') }}</span>
        <el-icon :size="18" class="collapse-icon" @click="sessionsCollapsed = !sessionsCollapsed">
          <DArrowLeft v-if="!sessionsCollapsed" />
          <DArrowRight v-else />
        </el-icon>
      </div>
      <template v-if="!sessionsCollapsed">
        <el-button type="primary" size="small" style="width:100%;margin-bottom:10px" @click="startNewSession">
          <el-icon><Plus /></el-icon>{{ $t('ai.chat.newSession') }}
        </el-button>
        <div class="sessions-list">
          <div v-if="sessions.length === 0" class="sessions-empty">
            {{ $t('ai.chat.noSessions') }}
          </div>
          <div
            v-for="s in sessions"
            :key="s.sessionId"
            :class="['session-item', { active: sessionId === s.sessionId }]"
            @click="handleSwitchSession(s.sessionId)"
          >
            <div class="session-info">
              <div class="session-title" :title="s.title">{{ s.title }}</div>
              <div class="session-meta">
                <span>{{ $t('ai.chat.msgCount', { n: s.messageCount }) }}</span>
                <span>{{ formatSessionTime(s.updateTime) }}</span>
              </div>
            </div>
            <el-button
              :icon="Delete"
              size="small"
              text
              class="session-delete"
              @click.stop="handleDeleteSession(s.sessionId)"
            />
          </div>
        </div>
      </template>
    </div>

    <!-- 中部：对话面板 -->
    <div class="chat-panel">
      <div class="chat-header">
        <el-icon :size="20"><ChatDotRound /></el-icon>
        <span>{{ currentSessionTitle || 'AI 智能助手' }}</span>
        <el-tag size="small" :type="llmStatus ? 'success' : 'danger'">
          {{ llmStatus ? $t('ai.chat.llmConnected') : $t('ai.chat.llmDisconnected') }}
        </el-tag>
        <div style="flex:1" />

        <!-- 服务状态 + 缓存统计（紧凑收纳在顶部） -->
        <el-popover placement="bottom" :width="220" trigger="hover">
          <template #reference>
            <el-button size="small" text class="header-status-btn">
              <el-icon :size="14"><InfoFilled /></el-icon>
              <span>状态</span>
            </el-button>
          </template>
          <div class="status-popover">
            <div class="status-row">
              <span class="status-label">活跃会话</span>
              <span class="status-value">{{ chatStatus?.activeSessions ?? '-' }}</span>
            </div>
            <div class="status-row">
              <span class="status-label">缓存命中率</span>
              <span class="status-value">{{ cacheStats ? (cacheStats.hitRate * 100).toFixed(1) + '%' : '-' }}</span>
            </div>
            <div class="status-row">
              <span class="status-label">缓存条目</span>
              <span class="status-value">{{ cacheStats ? cacheStats.totalEntries + ' / ' + cacheStats.maxSize : '-' }}</span>
            </div>
          </div>
        </el-popover>

        <el-tooltip :content="$t('ai.chat.clearSession')" placement="bottom">
          <el-button size="small" text @click="clearCurrentSession">
            <el-icon><Delete /></el-icon>
          </el-button>
        </el-tooltip>
      </div>

      <!-- 对话区 -->
      <div class="chat-messages" ref="msgContainer">
        <div v-if="loadingHistory" class="chat-placeholder">
          <el-icon :size="32" class="is-loading"><Loading /></el-icon>
          <p>{{ $t('ai.chat.loadingHistory') }}</p>
        </div>

        <div v-else-if="messages.length === 0" class="chat-placeholder">
          <el-icon :size="48" color="#ccc"><ChatDotRound /></el-icon>
          <p>我是AI智能助手，可以帮你：</p>
          <div class="quick-actions">
            <el-button v-for="q in quickQuestions" :key="q" size="small" @click="handleQuick(q)">
              {{ q }}
            </el-button>
          </div>
        </div>

        <div v-for="(msg, idx) in messages" :key="idx" :class="['msg-row', msg.role]">
          <div class="msg-avatar">
            <el-avatar :size="32" v-if="msg.role === 'user'">
              {{ userStore.nickname?.charAt(0) || 'U' }}
            </el-avatar>
            <el-icon v-else :size="32" color="#409eff"><Cpu /></el-icon>
          </div>
          <div class="msg-bubble">
            <!-- 用户附件预览 -->
            <div v-if="msg.attachments?.length" class="msg-attachments">
              <div v-for="(att, aidx) in msg.attachments" :key="aidx" :class="['attachment-item', att.type]">
                <el-image
                  v-if="att.type === 'image' && att.url"
                  :src="att.url"
                  :preview-src-list="[att.url]"
                  fit="cover"
                  class="msg-image"
                />
                <div v-else class="file-item">
                  <el-icon><Document /></el-icon>
                  <span class="file-name" :title="att.name">{{ att.name }}</span>
                </div>
              </div>
            </div>

            <!-- 消息内容（Markdown 文档化 + 代码树型） -->
            <div v-if="messageSegments[idx] && messageSegments[idx].length > 0" class="msg-content">
              <template v-for="seg in messageSegments[idx]" :key="seg.index">
                <!-- 文本文档：marked 渲染的 HTML -->
                <div v-if="seg.type === 'text'" class="text-segment markdown-body" v-html="seg.content"></div>

                <!-- 代码块：树型折叠节点 -->
                <div v-else class="code-tree-node">
                  <div
                    class="code-tree-header"
                    @click="toggleCodeCollapse(idx, seg.index)"
                  >
                    <span class="code-toggle-icon">{{ isCodeCollapsed(idx, seg.index) ? '▶' : '▼' }}</span>
                    <el-tag size="small" effect="dark" class="code-lang-tag">{{ seg.language }}</el-tag>
                    <span class="code-lines-hint">{{ countLines(seg.content) }} 行</span>
                    <el-button
                      size="small"
                      text
                      class="code-copy-btn"
                      @click.stop="copyCodeBlock(seg.content)"
                    >
                      <el-icon :size="14"><CopyDocument /></el-icon> 复制
                    </el-button>
                  </div>
                  <div v-show="!isCodeCollapsed(idx, seg.index)" class="code-tree-content">
                    <pre><code>{{ seg.content }}</code></pre>
                  </div>
                </div>
              </template>
            </div>

            <!-- AI 元数据标签行 -->
            <div v-if="msg.role === 'assistant' && msg.meta" class="msg-meta-row">
              <!-- 复杂度标签 -->
              <el-tag
                v-if="msg.meta.complexity"
                :type="complexityType(msg.meta.complexity)"
                effect="plain"
                class="meta-tag"
              >
                {{ $t('ai.chat.complexity.' + msg.meta.complexity) }}
              </el-tag>

              <!-- 缓存命中 -->
              <el-tag v-if="msg.meta.cached" type="warning" effect="plain" class="meta-tag">
                ⚡ {{ $t('ai.chat.cacheHit') }}
              </el-tag>

              <!-- 多Agent协作：展示参与的Agent，悬浮查看执行概况，点击可查看子任务明细 -->
              <el-tooltip
                v-if="msg.meta.orchestrated"
                placement="top"
                :content="orchestrationTip(msg.meta)"
              >
                <el-tag type="primary" effect="plain" class="meta-tag">
                  🤖 多Agent协作
                  <span v-if="msg.meta.agentIds && msg.meta.agentIds.length">
                    （{{ msg.meta.agentIds.map(agentLabel).join(' + ') }}）
                  </span>
                </el-tag>
              </el-tooltip>

              <!-- RAG 引用来源 -->
              <el-tag
                v-if="msg.meta.ragReferences && msg.meta.ragReferences.length"
                type="success"
                effect="plain"
                class="meta-tag"
              >
                📚 引用 {{ msg.meta.ragReferences.length }} 条知识
              </el-tag>

              <!-- 上下文压缩 -->
              <el-tag v-if="msg.meta.compressed" type="info" effect="plain" class="meta-tag">
                📦 {{ $t('ai.chat.contextCompressed') }}
              </el-tag>

              <!-- 模型标识 -->
              <el-tag v-if="msg.meta.modelUsed" type="info" effect="plain" class="meta-tag">
                {{ msg.meta.modelUsed }}
              </el-tag>

              <!-- 供应商标识 -->
              <el-tag v-if="msg.meta.providerName" type="primary" effect="plain" class="meta-tag">
                <el-icon :size="12" style="margin-right:2px"><Cpu /></el-icon>
                {{ msg.meta.providerName }}
              </el-tag>

              <!-- 主备切换提示 -->
              <el-tag v-if="msg.meta.failoverProvider" type="danger" effect="plain" class="meta-tag">
                ⚠ 主供应商({{ msg.meta.failoverProvider }})故障，已切换备用
              </el-tag>

              <!-- 耗时 -->
              <span v-if="msg.meta.elapsedMs" class="meta-time">
                {{ msg.meta.elapsedMs }}ms
              </span>
            </div>

            <!-- 文件相关性分析 -->
            <div v-if="msg.role === 'assistant' && msg.meta?.fileRelevance?.length" class="file-relevance">
              <div class="relevance-title">{{ $t('ai.chat.fileRelevance.primary') }}</div>
              <div
                v-for="fr in msg.meta.fileRelevance"
                :key="fr.fileName"
                :class="['relevance-item', { primary: fr.isPrimary }]"
              >
                <span class="relevance-name">{{ fr.fileName }}</span>
                <el-progress
                  :percentage="Math.round(fr.relevanceScore * 100)"
                  :stroke-width="4"
                  :show-text="false"
                  style="width:80px"
                />
                <span class="relevance-pct">{{ (fr.relevanceScore * 100).toFixed(0) }}%</span>
              </div>
            </div>

            <!-- 工具调用标签（含个数徽章） -->
            <div v-if="msg.tools?.length" class="msg-tools">
              <span class="tool-count-badge">
                <el-icon class="tool-icon"><Tools /></el-icon>
                {{ msg.tools.length }} 个工具
              </span>
              <div class="tool-tag-list">
                <el-tag
                  v-for="t in msg.tools"
                  :key="t"
                  size="small"
                  type="info"
                  effect="plain"
                  class="tool-tag"
                >
                  {{ t }}
                </el-tag>
              </div>
            </div>

            <!-- RAG 引用知识来源列表 -->
            <div v-if="msg.meta?.ragReferences?.length" class="rag-references">
              <div class="rag-title">
                <el-icon :size="13"><Document /></el-icon>
                引用的知识来源
              </div>
              <div
                v-for="(ref, idx) in msg.meta.ragReferences"
                :key="idx"
                class="rag-item"
              >
                <div class="rag-head">
                  <span class="rag-index">{{ idx + 1 }}</span>
                  <span class="rag-source-type">[{{ ref.sourceType || 'RAG' }}]</span>
                  <span class="rag-source">{{ ref.source || ref.title || '未知来源' }}</span>
                  <span v-if="ref.score != null" class="rag-score">
                    相关度 {{ (ref.score * 100).toFixed(0) }}%
                  </span>
                </div>
                <div v-if="ref.content" class="rag-content">{{ ref.content }}</div>
              </div>
            </div>

            <!-- 操作栏 -->
            <div class="msg-actions">
              <span class="msg-time">{{ formatMessageTime(msg) }}</span>
              <el-button
                v-if="msg.role === 'assistant' && (msg.meta || msg.tools?.length)"
                size="small"
                text
                @click="openMsgDetail(msg)"
              >
                <el-icon :size="14"><InfoFilled /></el-icon> 详情
              </el-button>
              <el-button
                v-if="msg.role === 'assistant'"
                :icon="CopyDocument"
                size="small"
                text
                @click="copyContent(msg.content)"
              />
              <el-button
                :icon="Delete"
                size="small"
                text
                type="danger"
                class="msg-delete-btn"
                @click="handleDeleteMessage(idx)"
              />
            </div>
          </div>
        </div>

        <!-- 流式输出中 -->
        <div v-if="streaming" class="msg-row assistant">
          <div class="msg-avatar"><el-icon :size="32" color="#409eff"><Cpu /></el-icon></div>
          <div class="msg-bubble">
            <div class="msg-content">
              {{ streamingText || transitionText }}
              <span v-if="!hasReceivedToken" class="cursor-blink">|</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="chat-input" @dragover.prevent @drop.prevent="handleDrop">
        <div class="attachment-preview" v-if="attachments.length > 0">
          <div v-for="(att, idx) in attachments" :key="idx" :class="['preview-item', att.type]">
            <el-image v-if="att.type === 'image' && att.url" :src="att.url" fit="cover" class="preview-image" />
            <div v-else class="preview-file">
              <el-icon><Document /></el-icon>
              <span class="file-name" :title="att.name">{{ att.name }}</span>
            </div>
            <el-icon class="remove-icon" @click="removeAttachment(idx)"><Close /></el-icon>
          </div>
        </div>
        <el-input
          v-model="inputText"
          :placeholder="$t('ai.chat.pleaseEnter')"
          type="textarea"
          :rows="2"
          :disabled="streaming"
          @keyup.enter.exact="handleSend"
          @paste="handlePaste"
        />
        <div class="input-actions">
          <span class="session-id" v-if="sessionId">会话: {{ sessionId.slice(0, 8) }}...</span>
          <el-upload
            ref="uploadRef"
            action="#"
            :auto-upload="false"
            :show-file-list="false"
            :multiple="true"
            :on-change="handleFileChange"
            class="upload-trigger"
          >
            <el-button size="small" :icon="Paperclip" :disabled="streaming">附件</el-button>
          </el-upload>
          <el-tooltip content="开启后模型会先展示推理过程，再给出最终回答" placement="top">
            <el-switch
              v-model="thinking"
              active-text="思考模式"
              inline-prompt
              :disabled="streaming"
              class="thinking-switch"
            />
          </el-tooltip>
          <el-button
            type="primary"
            :icon="streaming ? undefined : Promotion"
            :loading="streaming"
            @click="handleSend"
          >
            {{ streaming ? $t('ai.chat.replying') : $t('ai.chat.send') }}
          </el-button>
        </div>
      </div>
    </div>

  </div>

  <!-- 消息详情抽屉 -->
  <el-drawer
    v-model="detailVisible"
    title="消息运行详情"
    size="380px"
    destroy-on-close
  >
    <div v-if="detailMsg" class="msg-detail">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="角色">{{ detailMsg.role === 'assistant' ? 'AI 助手' : '用户' }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ formatMessageTime(detailMsg) }}</el-descriptions-item>
        <el-descriptions-item v-if="detailMsg.meta?.modelUsed" label="模型">{{ detailMsg.meta.modelUsed }}</el-descriptions-item>
        <el-descriptions-item v-if="detailMsg.meta?.providerName" label="供应商">{{ detailMsg.meta.providerName }}</el-descriptions-item>
        <el-descriptions-item v-if="detailMsg.meta?.elapsedMs != null" label="耗时">{{ detailMsg.meta.elapsedMs }} ms</el-descriptions-item>
              <el-descriptions-item v-if="detailMsg.meta?.complexity" label="复杂度">
                <el-tag :type="complexityType(detailMsg.meta.complexity)" size="small">{{ detailMsg.meta.complexity }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item v-if="detailMsg.meta?.orchestrated" label="协作方式">
                <el-tag type="primary" size="small">多Agent编排</el-tag>
              </el-descriptions-item>
              <el-descriptions-item v-if="detailMsg.meta?.agentIds?.length" label="参与Agent">
                {{ detailMsg.meta.agentIds.map(agentLabel).join('、') }}
              </el-descriptions-item>
              <el-descriptions-item v-if="detailMsg.meta?.taskCount" label="子任务数">
                {{ detailMsg.meta.taskCount }} 个（{{ detailMsg.meta.layerCount || 1 }} 层）
              </el-descriptions-item>
              <el-descriptions-item v-if="detailMsg.meta?.orchestrationElapsedMs" label="编排耗时">
                {{ detailMsg.meta.orchestrationElapsedMs }} ms
              </el-descriptions-item>
              <el-descriptions-item v-if="detailMsg.meta?.traceId" label="链路追踪ID">
                <el-text type="info" size="small">{{ detailMsg.meta.traceId }}</el-text>
              </el-descriptions-item>
        <el-descriptions-item label="缓存命中">
          <el-tag :type="detailMsg.meta?.cached ? 'warning' : 'info'" size="small">
            {{ detailMsg.meta?.cached ? '是' : '否' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="上下文压缩">
          <el-tag :type="detailMsg.meta?.compressed ? 'warning' : 'info'" size="small">
            {{ detailMsg.meta?.compressed ? '是' : '否' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <div v-if="detailMsg.tools?.length" class="detail-section">
        <div class="detail-title">调用的工具（{{ detailMsg.tools.length }} 个）</div>
        <div class="detail-tags">
          <el-tag v-for="t in detailMsg.tools" :key="t" size="small" type="info" effect="plain">{{ t }}</el-tag>
        </div>
      </div>

      <div v-if="detailMsg.meta?.ragReferences?.length" class="detail-section">
        <div class="detail-title">RAG 引用（{{ detailMsg.meta.ragReferences.length }} 条）</div>
        <div v-for="(ref, i) in detailMsg.meta.ragReferences" :key="i" class="detail-ref">
          <div class="ref-head">
            <span class="ref-index">{{ i + 1 }}</span>
            <span class="ref-source">{{ ref.source || ref.title || '未知来源' }}</span>
            <span v-if="ref.score != null" class="ref-score">{{ (ref.score * 100).toFixed(0) }}%</span>
          </div>
          <div v-if="ref.content" class="ref-content">{{ ref.content }}</div>
        </div>
      </div>

      <div v-if="detailMsg.meta?.reasoning" class="detail-section">
        <div class="detail-title">推理过程</div>
        <pre class="reasoning-block">{{ detailMsg.meta.reasoning }}</pre>
      </div>

      <div v-if="detailMsg.meta?.fileRelevance?.length" class="detail-section">
        <div class="detail-title">附件相关性</div>
        <div v-for="fr in detailMsg.meta.fileRelevance" :key="fr.fileName" class="detail-relevance">
          <span>{{ fr.fileName }}</span>
          <el-progress :percentage="Math.round(fr.relevanceScore * 100)" :stroke-width="6" style="width:120px" />
          <span>{{ (fr.relevanceScore * 100).toFixed(0) }}%</span>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound, Cpu, Promotion, Paperclip, Document, Close,
  Delete, Plus, DArrowLeft, DArrowRight, Loading, CopyDocument, InfoFilled,
  Tools
} from '@element-plus/icons-vue'
import {
  chatAsk, chatStream, chatStatus as getStatus, createSession, listSessions,
  switchSession, loadSessionHistory, clearSession as clearSessionApi,
  getCacheStats, deleteMessage as deleteMsgApi
} from '@/api/ai'
import { useUserStore } from '@/stores/user'
import dayjs from 'dayjs'
import { marked, Renderer } from 'marked'
import type { ChatAttachment, ChatResponseData, ChatSessionVO, CacheStatsVO } from '@/types'

const userStore = useUserStore()

interface MsgMeta {
  complexity?: string
  cached?: boolean
  compressed?: boolean
  modelUsed?: string
  elapsedMs?: number
  fileRelevance?: Array<{ fileName: string; relevanceScore: number; isPrimary: boolean }>
  /** 实际使用的供应商编码 */
  providerCode?: string
  /** 实际使用的供应商名称 */
  providerName?: string
  /** 主供应商失败后切换到的备用供应商名称 */
  failoverProvider?: string
  /** 模型推理过程（思考模式） */
  reasoning?: string
  /** 是否由多 Agent 编排产出 */
  orchestrated?: boolean
  /** 参与协作的 Agent 标识 */
  agentIds?: string[]
  /** 编排链路追踪ID，可用于查询子Agent调用明细 */
  traceId?: string
  /** 子任务总数 */
  taskCount?: number
  /** 执行层级数 */
  layerCount?: number
  /** 编排整体耗时毫秒 */
  orchestrationElapsedMs?: number
  /** RAG 检索并引用的知识来源 */
  ragReferences?: Array<{
    source?: string
    title?: string
    content?: string
    score?: number
    sourceType?: string
  }>
}

interface ChatMsg {
  role: 'user' | 'assistant' | 'system'
  content: string
  time: string
  /** 服务端返回的消息创建时间，优先显示 */
  createTime?: string
  tools?: string[]
  attachments?: ChatAttachment[]
  meta?: MsgMeta
}

// ==================== 状态 ====================
const inputText = ref('')
const messages = ref<ChatMsg[]>([])
const streaming = ref(false)
const streamingText = ref('')
const hasReceivedToken = ref(false)
const lastAttachmentCount = ref(0)
const thinking = ref(true)
const sessionId = ref('')
const currentSessionTitle = ref('')
const llmStatus = ref(true)
const chatStatus = ref<any>(null)
const msgContainer = ref<HTMLElement>()
const attachments = ref<ChatAttachment[]>([])
const uploadRef = ref<any>(null)
const loadingHistory = ref(false)
const sessionsCollapsed = ref(false)

// 会话列表
const sessions = ref<ChatSessionVO[]>([])

// 缓存统计
const cacheStats = ref<CacheStatsVO | null>(null)

// 消息详情抽屉
const detailVisible = ref(false)
const detailMsg = ref<ChatMsg | null>(null)

// 当前会话消息数实时同步到左侧列表
watch(() => messages.value.length, () => {
  if (!sessionId.value) return
  const idx = sessions.value.findIndex(s => s.sessionId === sessionId.value)
  if (idx >= 0) {
    sessions.value[idx].messageCount = messages.value.length
  }
})

const userId = computed(() => String(userStore.userInfo?.id || ''))
const transitionText = computed(() => {
  return lastAttachmentCount.value > 0 ? '正在分析附件并思考...' : 'AI 正在思考...'
})

const MAX_ATTACHMENTS = 5
const ALLOWED_IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/jpg', 'image/gif', 'image/webp', 'image/bmp']
const MAX_FILE_SIZE = 20 * 1024 * 1024

const quickQuestions = [
  '当前有多少待审批任务？',
  '查看预警汇总',
  '获取业务指标',
  '列出知识库',
  '最近7天的消息发送情况',
  '工作流审批量有没有异常？'
]

// ==================== 初始化 ====================
// alive 标记组件是否仍处于挂载状态，避免在流式响应未结束时切走页面
// 导致 finally 中操作已卸载组件而抛出未捕获异常（会卸载整个 Vue 应用根）
const alive = ref(true)
onMounted(async () => {
  await Promise.allSettled([
    loadSessions(),
    loadServiceStatus(),
    loadCacheStats(),
  ])
})
onUnmounted(() => {
  alive.value = false
})

async function loadSessions() {
  if (!userId.value) return
  try {
    const res = await listSessions(userId.value)
    sessions.value = res?.data || []
  } catch { /* */ }
}

async function loadServiceStatus() {
  try {
    const res = await getStatus()
    chatStatus.value = res?.data
  } catch { /* */ }
}

async function loadCacheStats() {
  try {
    const res = await getCacheStats()
    cacheStats.value = res?.data || null
  } catch { /* */ }
}

/**
 * 统一规范化 toolsUsed 字段。
 * 后端在不同接口中可能返回 string[] 或 JSON 字符串（如 '["listKnowledgeBases"]"），
 * 前端统一按数组处理，避免字符串被 v-for 拆成单个字符显示。
 */
function normalizeTools(raw: unknown): string[] | undefined {
  if (raw == null) return undefined
  if (Array.isArray(raw)) {
    return raw.filter((item): item is string => typeof item === 'string' && item.length > 0)
  }
  if (typeof raw === 'string') {
    const trimmed = raw.trim()
    if (!trimmed) return undefined
    if (trimmed.startsWith('[')) {
      try {
        const parsed = JSON.parse(trimmed) as unknown
        return normalizeTools(parsed)
      } catch {
        return [trimmed]
      }
    }
    return [trimmed]
  }
  return undefined
}

// ==================== 会话操作 ====================
/** 点击“新建对话”：仅清空当前状态，不立即创建会话 */
function startNewSession() {
  sessionId.value = ''
  currentSessionTitle.value = ''
  messages.value = []
  inputText.value = ''
  attachments.value = []
}

/** 首次发送消息时真正创建会话 */
async function handleNewSession(title?: string) {
  if (!userId.value) {
    ElMessage.warning('请先登录')
    return
  }
  try {
    const res = await createSession(userId.value, title)
    const sess = res?.data
    if (sess) {
      sessionId.value = sess.sessionId
      currentSessionTitle.value = sess.title
      messages.value = []
    }
    await loadSessions()
  } catch {
    ElMessage.error('创建会话失败')
  }
}

async function handleSwitchSession(sid: string) {
  if (sid === sessionId.value) return
  loadingHistory.value = true
  sessionId.value = sid
  messages.value = []

  try {
    // 通知后端切换会话
    await switchSession(sid)

    // 加载历史消息
    const res = await loadSessionHistory(sid)
    const data = res?.data
    if (data) {
      currentSessionTitle.value = data.title
      // 转换消息格式（统一 role 小写，确保头像/样式判断正确）
      messages.value = (data.messages || []).map(m => ({
        role: (m.role || 'assistant').toLowerCase() as 'user' | 'assistant' | 'system',
        content: m.content || '',
        createTime: m.createTime || '',
        time: m.createTime ? dayjs(m.createTime).format('YYYY-MM-DD HH:mm:ss') : '',
        tools: normalizeTools(m.toolsUsed),
        attachments: m.attachments
      }))
    }
  } catch {
    ElMessage.error('加载会话历史失败')
  } finally {
    loadingHistory.value = false
    await nextTick()
    scrollBottom()
  }
}

async function handleDeleteSession(sid: string) {
  try {
    await ElMessageBox.confirm('确定删除该会话吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await clearSessionApi(sid)
    if (sessionId.value === sid) {
      sessionId.value = ''
      currentSessionTitle.value = ''
      messages.value = []
    }
    await loadSessions()
    ElMessage.success('删除成功')
  } catch { /* 取消 */ }
}

async function clearCurrentSession() {
  if (messages.value.length === 0) return
  messages.value = []
  if (sessionId.value) {
    try {
      await clearSessionApi(sessionId.value)
    } catch { /* */ }
  }
  sessionId.value = ''
  currentSessionTitle.value = ''
  ElMessage.success('会话已清除')
}

// ==================== 发送消息 ====================
function handleQuick(q: string) {
  inputText.value = q
  handleSend()
}

async function handleSend() {
  const text = inputText.value.trim()
  if ((!text && attachments.value.length === 0) || streaming.value) return

  // 确保有会话（首次发送时用问题前 30 字作为标题）
  if (!sessionId.value) {
    await handleNewSession(text.slice(0, 30))
    if (!sessionId.value) return
  }

  const msgAttachments = attachments.value.map(a => ({
    type: a.type,
    name: a.name,
    url: a.url,
    mimeType: a.mimeType,
    size: a.size
  } as ChatAttachment))

  messages.value.push({
    role: 'user',
    content: text || '请分析附件',
    time: dayjs().format('YYYY-MM-DD HH:mm:ss'),
    createTime: dayjs().toISOString(),
    attachments: msgAttachments.length ? msgAttachments : undefined
  })

  const currentAttachments = [...attachments.value]
  const questionText = text || '请分析附件'
  inputText.value = ''
  attachments.value = []
  streaming.value = true
  streamingText.value = ''
  hasReceivedToken.value = false
  lastAttachmentCount.value = currentAttachments.length

  await nextTick()
  scrollBottom()

  try {
    const body = await chatStream({
      sessionId: sessionId.value,
      question: questionText,
      useMemory: true,
      thinking: thinking.value,
      attachments: currentAttachments,
      userId: userId.value
    })
    const reader = body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let currentEvent = 'message'
    let currentData = ''

    while (true) {
      // 组件已卸载则立即终止读取，避免操作已销毁的响应式状态
      if (!alive.value) break
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          currentData += line.slice(5)
        } else if (line === '') {
          // 空行 = 一个 SSE 事件结束
          handleSSEEvent(currentEvent, currentData.trim())
          currentEvent = 'message'
          currentData = ''
        }
      }
    }
    // 处理最后未完成的事件
    if (alive.value && currentData.trim()) {
      handleSSEEvent(currentEvent, currentData.trim())
    }
  } catch (error: any) {
    if (!alive.value) return // 卸载后忽略
    const errMsg = error?.message || '请求失败，请稍后重试'
    ElMessage.error(errMsg)
    if (!streamingText.value) {
      messages.value.push({
        role: 'assistant',
        content: errMsg,
        time: dayjs().format('YYYY-MM-DD HH:mm:ss')
      })
    }
  } finally {
    // 仅在组件存活时执行收尾（刷新会话、滚动到底），否则跳过以免崩溃
    if (!alive.value) return
    if (streamingText.value) {
      // 流中断但仍有文本：保留已接收的内容
      messages.value.push({
        role: 'assistant',
        content: streamingText.value,
        time: dayjs().format('YYYY-MM-DD HH:mm:ss')
      })
      streamingText.value = ''
    }
    streaming.value = false
    loadSessions()
    loadCacheStats()
    await nextTick()
    scrollBottom()
  }
}

/** 解析单个 SSE 事件 */
function handleSSEEvent(eventType: string, data: string) {
  switch (eventType) {
    case 'token':
      if (!hasReceivedToken.value) {
        hasReceivedToken.value = true
      }
      streamingText.value += data
      break
    case 'complete':
      // 解析 complete 事件携带的元数据 JSON（ChatResponseMeta）
      let meta: MsgMeta | undefined
      let toolsUsed: string[] | undefined
      let createTime: string | undefined
      try {
        const parsed = JSON.parse(data) as Record<string, any>
        if (parsed) {
          createTime = parsed.createTime
          meta = {
            complexity: parsed.complexity,
            cached: parsed.cached,
            compressed: parsed.compressed,
            modelUsed: parsed.modelUsed,
            elapsedMs: parsed.elapsedMs,
            providerCode: parsed.providerCode,
            providerName: parsed.providerName,
            failoverProvider: parsed.failoverProvider,
            reasoning: parsed.reasoning,
            fileRelevance: parsed.fileRelevance,
            ragReferences: parsed.ragReferences,
            orchestrated: parsed.orchestrated,
            agentIds: parsed.agentIds,
            traceId: parsed.traceId,
            taskCount: parsed.taskCount,
            layerCount: parsed.layerCount,
            orchestrationElapsedMs: parsed.orchestrationElapsedMs
          }
          toolsUsed = normalizeTools(parsed.toolsUsed)
          if (parsed.sessionTitle) {
            currentSessionTitle.value = parsed.sessionTitle
          }
        }
      } catch {
        // data 不是 JSON，忽略
      }
      messages.value.push({
        role: 'assistant',
        content: streamingText.value,
        time: createTime
          ? dayjs(createTime).format('YYYY-MM-DD HH:mm:ss')
          : dayjs().format('YYYY-MM-DD HH:mm:ss'),
        createTime: createTime || dayjs().toISOString(),
        tools: toolsUsed,
        meta
      })
      streamingText.value = ''
      break
    case 'error':
      if (streamingText.value) {
        messages.value.push({
          role: 'assistant',
          content: streamingText.value,
          time: dayjs().format('YYYY-MM-DD HH:mm:ss'),
          createTime: dayjs().toISOString()
        })
        streamingText.value = ''
      }
      messages.value.push({
        role: 'assistant',
        content: data || '请求失败',
        time: dayjs().format('YYYY-MM-DD HH:mm:ss'),
        createTime: dayjs().toISOString()
      })
      break
  }
}

// ==================== 附件处理 ====================
function handleFileChange(file: any) {
  const raw = file.raw as File
  if (!raw) return
  addFileAttachment(raw)
  if (uploadRef.value) uploadRef.value.clearFiles?.()
}

function addFileAttachment(file: File) {
  if (attachments.value.length >= MAX_ATTACHMENTS) {
    ElMessage.warning(`最多上传${MAX_ATTACHMENTS}个附件`)
    return
  }
  if (file.size > MAX_FILE_SIZE) {
    ElMessage.warning(`文件 "${file.name}" 超出 ${MAX_FILE_SIZE / 1024 / 1024}MB 限制`)
    return
  }
  const isImage = ALLOWED_IMAGE_TYPES.includes(file.type)
  const attachment: ChatAttachment = {
    type: isImage ? 'image' : 'file',
    name: file.name,
    raw: file,
    mimeType: file.type,
    size: file.size
  }
  if (isImage) attachment.url = URL.createObjectURL(file)
  attachments.value.push(attachment)
}

function handlePaste(e: ClipboardEvent) {
  const items = e.clipboardData?.items
  if (!items) return
  let hasImage = false
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (file) { hasImage = true; addImageFromFile(file, '粘贴图片') }
    }
  }
  if (hasImage) e.preventDefault()
}

function addImageFromFile(file: File, defaultName: string) {
  if (attachments.value.length >= MAX_ATTACHMENTS) {
    ElMessage.warning(`最多上传${MAX_ATTACHMENTS}个附件`)
    return
  }
  const reader = new FileReader()
  reader.onload = (ev) => {
    const dataUrl = ev.target?.result as string
    attachments.value.push({
      type: 'image',
      name: defaultName || file.name,
      url: dataUrl,
      raw: file,
      mimeType: file.type,
      size: file.size
    })
  }
  reader.readAsDataURL(file)
}

function handleDrop(e: DragEvent) {
  const files = e.dataTransfer?.files
  if (!files) return
  for (let i = 0; i < files.length; i++) {
    const file = files[i]
    if (file.type.startsWith('image/')) {
      addImageFromFile(file, file.name)
    } else {
      addFileAttachment(file)
    }
  }
}

function removeAttachment(idx: number) {
  const att = attachments.value[idx]
  if (att?.url && att.url.startsWith('blob:')) URL.revokeObjectURL(att.url)
  attachments.value.splice(idx, 1)
}

// ==================== 内容解析（Markdown + 代码树） ====================
interface ContentSegment {
  type: 'text' | 'code'
  /** 文本段：marked 渲染后的 HTML；代码段：原始代码文本 */
  content: string
  language?: string
  index: number
}

/** 每条消息的代码块折叠状态，key: msgIndex_segIndex */
const codeCollapsed = reactive<Record<string, boolean>>({})

/** marked 渲染器：禁用原生代码块渲染，由前端树型组件接管 */
const mdRenderer = new Renderer()
mdRenderer.code = () => ''
marked.use({ renderer: mdRenderer })

/** 将 AI 回复解析为文本/代码段 */
function parseSegments(text: string): ContentSegment[] {
  if (!text) return []
  const segments: ContentSegment[] = []
  const codeRegex = /```(\w*)\r?\n([\s\S]*?)```/g
  let lastIdx = 0
  let match

  while ((match = codeRegex.exec(text)) !== null) {
    // 代码块之前的文本
    if (match.index > lastIdx) {
      segments.push({
        type: 'text',
        content: marked.parse(text.slice(lastIdx, match.index)) as string,
        index: segments.length
      })
    }
    // 代码块
    const lang = match[1]?.trim() || 'text'
    segments.push({
      type: 'code',
      content: match[2].replace(/\n$/, ''),
      language: lang,
      index: segments.length
    })
    lastIdx = match.index + match[0].length
  }

  // 剩余文本
  if (lastIdx < text.length) {
    segments.push({
      type: 'text',
      content: marked.parse(text.slice(lastIdx)) as string,
      index: segments.length
    })
  }

  return segments
}

/** 每条消息预解析的内容段 */
const messageSegments = computed(() => {
  return messages.value.map(msg => parseSegments(msg.content))
})

function toggleCodeCollapse(msgIdx: number, segIdx: number) {
  const key = `${msgIdx}_${segIdx}`
  codeCollapsed[key] = !codeCollapsed[key]
}

function isCodeCollapsed(msgIdx: number, segIdx: number): boolean {
  return codeCollapsed[`${msgIdx}_${segIdx}`] === true
}

/**
 * Agent 标识 → 中文名称映射。
 * 与后端 AgentRegistry 中注册的 agentId 对应（metrics/alert/knowledge/query）。
 */
const AGENT_LABELS: Record<string, string> = {
  metrics: '指标分析',
  alert: '预警检测',
  knowledge: '知识检索',
  query: '数据查询'
}

/** 将 Agent 标识转为中文名称，未收录的原样返回 */
function agentLabel(agentId: string): string {
  return AGENT_LABELS[agentId] || agentId
}

/** 多Agent协作标签的悬浮提示，展示任务图结构与耗时 */
function orchestrationTip(meta: MsgMeta): string {
  const parts: string[] = []
  if (meta.taskCount) {
    parts.push(`${meta.taskCount} 个子任务`)
  }
  if (meta.layerCount) {
    parts.push(`${meta.layerCount} 层执行`)
  }
  if (meta.orchestrationElapsedMs) {
    parts.push(`耗时 ${meta.orchestrationElapsedMs} ms`)
  }
  if (meta.traceId) {
    parts.push(`链路 ${meta.traceId}`)
  }
  return parts.length ? parts.join(' · ') : '由多个专业 Agent 协作完成'
}

function complexityType(c: string): 'success' | 'warning' | 'danger' {
  return c === 'simple' ? 'success' : c === 'medium' ? 'warning' : 'danger'
}

function formatSessionTime(t: string): string {
  return t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : ''
}

function formatMessageTime(msg: ChatMsg): string {
  const t = msg.createTime || msg.time
  if (!t) return ''
  return dayjs(t).format('YYYY-MM-DD HH:mm:ss')
}

async function copyContent(text: string) {
  if (!text) {
    ElMessage.warning('没有可复制的内容')
    return
  }
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      ElMessage.success('已复制')
      return
    }
    // 降级方案：临时 textarea + execCommand
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.style.position = 'fixed'
    textarea.style.left = '-9999px'
    textarea.style.top = '0'
    textarea.setAttribute('readonly', '')
    document.body.appendChild(textarea)
    textarea.select()
    textarea.setSelectionRange(0, text.length)
    const ok = document.execCommand('copy')
    document.body.removeChild(textarea)
    if (ok) {
      ElMessage.success('已复制')
    } else {
      throw new Error('execCommand copy failed')
    }
  } catch {
    ElMessage.error('复制失败，请手动选中复制')
  }
}

function countLines(text: string): number {
  return text ? text.split('\n').length : 0
}

async function copyCodeBlock(code: string) {
  await copyContent(code)
}

function openMsgDetail(msg: ChatMsg) {
  detailMsg.value = msg
  detailVisible.value = true
}

function scrollBottom() {
  if (msgContainer.value) {
    msgContainer.value.scrollTop = msgContainer.value.scrollHeight
  }
}

async function handleDeleteMessage(idx: number) {
  const msg = messages.value[idx]
  if (!msg || !sessionId.value) return
  try {
    await ElMessageBox.confirm('确定删除这条消息吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    try {
      await deleteMsgApi(sessionId.value, idx)
    } catch {
      // 后端删除失败不阻塞前端
    }
    messages.value.splice(idx, 1)
    ElMessage.success('已删除')
  } catch {
    // 用户取消
  }
}
</script>

<style scoped lang="scss">
.chat-layout {
  display: flex;
  gap: 12px;
  height: calc(100vh - 120px);
}

.header-status-btn {
  padding: 4px 8px;
  .el-icon { margin-right: 3px; }
}

.status-popover {
  .status-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 6px 0;
    border-bottom: 1px solid #f0f0f0;
    &:last-child { border-bottom: none; }
  }
  .status-label { font-size: 13px; color: #606266; }
  .status-value { font-size: 13px; color: #303133; font-weight: 500; }
}

// ==================== 左侧会话列表 ====================
.chat-sessions {
  width: 220px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,.08);
  display: flex;
  flex-direction: column;
  padding: 12px;
  transition: width 0.25s;
  overflow: hidden;

  &.collapsed {
    width: 44px;
    padding: 12px 6px;
  }
}

.sessions-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 10px;
  color: #303133;
}

.collapse-icon {
  cursor: pointer;
  color: #909399;
  &:hover { color: #409eff; }
}

.sessions-list {
  flex: 1;
  overflow-y: auto;
  margin-top: 6px;
}

.sessions-empty {
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  padding: 20px 0;
}

.session-item {
  display: flex;
  align-items: center;
  padding: 10px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: background 0.15s, border-color 0.15s;
  border-left: 3px solid transparent;

  &:hover { background: #f0f2f5; }
  &.active {
    background: #ecf5ff;
    border-left-color: #409eff;
  }

  .session-info {
    flex: 1;
    min-width: 0;
  }

  .session-title {
    font-size: 13px;
    color: #303133;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .session-meta {
    font-size: 11px;
    color: #c0c4cc;
    margin-top: 3px;
    display: flex;
    gap: 8px;
  }

  .session-delete {
    opacity: 0;
    transition: opacity 0.15s;
    flex-shrink: 0;
  }

  &:hover .session-delete { opacity: 1; }
}

// ==================== 中部对话面板 ====================
.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,.08);
  overflow: hidden;
  min-width: 0;
}

.chat-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
  font-weight: 600;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #f5f7fa;
}

.chat-placeholder {
  text-align: center;
  padding: 60px 0;
  color: #909399;
  p { margin: 12px 0 16px; }
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.msg-row {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  &.user { flex-direction: row-reverse; }
  &.system {
    justify-content: center;
    .msg-bubble {
      background: #fdf6ec;
      border: 1px dashed #e6a23c;
      max-width: 90%;
      font-size: 12px;
      color: #909399;
    }
  }

  .msg-avatar {
    .el-avatar {
      background: #409eff;
      color: #fff;
      font-size: 14px;
      font-weight: 500;
    }
  }
}

.msg-row:hover .msg-delete-btn {
  opacity: 1;
}

.msg-avatar {
  flex-shrink: 0;
}

.msg-bubble {
  max-width: 75%;
  padding: 10px 14px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0,0,0,.06);
  .user & { background: #ecf5ff; }
}

.msg-content {
  line-height: 1.7;
  word-break: break-word;

  // Markdown 文档化样式
  .markdown-body {
    margin-bottom: 8px;

    :deep(h1), :deep(h2), :deep(h3), :deep(h4) {
      margin: 12px 0 6px;
      font-weight: 600;
      line-height: 1.4;
      color: #303133;
    }
    :deep(h1) { font-size: 18px; border-bottom: 1px solid #ebeef5; padding-bottom: 4px; }
    :deep(h2) { font-size: 16px; }
    :deep(h3) { font-size: 15px; }
    :deep(h4) { font-size: 14px; }

    :deep(p) { margin: 4px 0; }
    :deep(ul), :deep(ol) { padding-left: 20px; margin: 6px 0; }
    :deep(li) { margin: 2px 0; }
    :deep(blockquote) {
      margin: 6px 0;
      padding: 6px 12px;
      border-left: 3px solid #dfe2e5;
      background: #f6f8fa;
      color: #606266;
    }
    :deep(table) {
      border-collapse: collapse;
      margin: 8px 0;
      width: 100%;
      th, td { border: 1px solid #dfe2e5; padding: 6px 10px; font-size: 13px; text-align: left; }
      th { background: #f5f7fa; font-weight: 600; }
    }
    :deep(code) {
      background: #f0f0f0;
      padding: 1px 5px;
      border-radius: 3px;
      font-size: 13px;
      color: #e74c3c;
    }
    :deep(strong) { color: #303133; font-weight: 600; }
    :deep(em) { font-style: italic; }
    :deep(a) { color: #409eff; text-decoration: none; &:hover { text-decoration: underline; } }
    :deep(hr) { border: none; border-top: 1px solid #ebeef5; margin: 12px 0; }
  }

  // 消息详情抽屉样式
  .msg-detail {
    .detail-section {
      margin-top: 16px;
    }
    .detail-title {
      font-size: 13px;
      font-weight: 600;
      color: #303133;
      margin-bottom: 8px;
    }
    .detail-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }
    .detail-ref {
      padding: 8px;
      background: #f5f7ff;
      border-radius: 6px;
      margin-bottom: 6px;
      .ref-head {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 12px;
      }
      .ref-index {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 16px;
        height: 16px;
        border-radius: 50%;
        background: #6366f1;
        color: #fff;
        font-size: 10px;
      }
      .ref-source {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .ref-score { color: #67c23a; }
      .ref-content {
        margin-top: 4px;
        font-size: 12px;
        color: #606266;
        line-height: 1.5;
      }
    }
    .reasoning-block {
      background: #f5f7fa;
      padding: 10px;
      border-radius: 6px;
      font-size: 12px;
      line-height: 1.6;
      white-space: pre-wrap;
      word-break: break-word;
      margin: 0;
    }
    .detail-relevance {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 12px;
      margin-bottom: 6px;
      span:first-child { flex: 1; }
    }
  }

  // 内联代码块回退
  :deep(pre) {
    background: #1e1e1e;
    color: #d4d4d4;
    padding: 12px 16px;
    border-radius: 6px;
    overflow-x: auto;
    margin: 8px 0;
    font-size: 13px;
    line-height: 1.5;
    code { background: transparent; padding: 0; color: inherit; }
  }
}

.msg-meta-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 6px;
  .meta-tag {
    font-size: 11px;
    height: 20px;
    line-height: 18px;
    padding: 0 6px;
  }
  .meta-time {
    font-size: 11px;
    color: #c0c4cc;
    margin-left: auto;
  }
}

.msg-tools {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 6px 10px;
  background: rgba(64, 158, 255, 0.06);
  border: 1px solid rgba(64, 158, 255, 0.12);
  border-radius: 8px;
  width: fit-content;

  .tool-count-badge {
    font-size: 12px;
    background: #e6f2ff;
    color: #1976d2;
    padding: 2px 10px;
    border-radius: 12px;
    font-weight: 600;
    line-height: 20px;
    height: 24px;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    transition: background 0.2s;
    &:hover {
      background: #d4e9ff;
    }
  }

  .tool-tag-list {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;
  }

  .tool-tag {
    font-size: 12px;
    height: 24px;
    line-height: 22px;
    padding: 0 10px;
    background: #ffffff;
    color: #409eff;
    border-color: #b3d8ff;
    border-radius: 12px;
    font-weight: 500;
    transition: all 0.2s;
    &:hover {
      background: #409eff;
      color: #ffffff;
      border-color: #409eff;
    }
  }

  .tool-icon {
    font-size: 13px;
    margin-right: 2px;
  }
}

// ==================== 代码树型节点 ====================
.code-tree-node {
  margin: 8px 0;
  border: 1px solid #dfe2e5;
  border-radius: 6px;
  overflow: hidden;
  background: #fafbfc;

  .code-tree-header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 12px;
    background: #f0f2f5;
    cursor: pointer;
    user-select: none;
    transition: background 0.15s;
    &:hover { background: #e8ebef; }

    .code-toggle-icon {
      font-size: 10px;
      color: #909399;
      width: 14px;
      text-align: center;
    }

    .code-lang-tag {
      text-transform: uppercase;
      font-size: 11px;
    }

    .code-lines-hint {
      font-size: 11px;
      color: #909399;
      flex: 1;
    }

    .code-copy-btn {
      font-size: 12px;
      color: #606266;
      padding: 2px 6px;
      &:hover { color: #409eff; }
    }
  }

  .code-tree-content {
    pre {
      margin: 0;
      padding: 12px 16px;
      background: #1e1e1e;
      color: #e0e0e0;
      font-size: 13px;
      line-height: 1.6;
      overflow-x: auto;
      border-radius: 0;
      white-space: pre;
      code {
        background: transparent;
        color: inherit;
        padding: 0;
        font-family: 'Fira Code', 'Cascadia Code', Consolas, monospace;
      }
    }
  }
}

.file-relevance {
  margin-top: 8px;
  padding: 8px 10px;
  background: #f9fafb;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  .relevance-title {
    font-size: 12px;
    color: #909399;
    margin-bottom: 4px;
  }
  .relevance-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 3px 0;
    font-size: 12px;
    &.primary {
      color: #409eff;
      font-weight: 500;
    }
  }
  .relevance-name {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .relevance-pct {
    font-size: 11px;
    color: #909399;
    min-width: 32px;
    text-align: right;
  }
}

.rag-references {
  margin-top: 8px;
  padding: 8px 10px;
  background: #f5f7ff;
  border-radius: 6px;
  border: 1px solid #e0e7ff;
  .rag-title {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: #6366f1;
    font-weight: 500;
    margin-bottom: 6px;
  }
  .rag-item {
    padding: 5px 0;
    border-top: 1px dashed #e0e7ff;
    &:first-of-type {
      border-top: none;
    }
  }
  .rag-head {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    flex-wrap: wrap;
  }
  .rag-index {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 16px;
    height: 16px;
    border-radius: 50%;
    background: #6366f1;
    color: #fff;
    font-size: 10px;
    flex-shrink: 0;
  }
  .rag-source-type {
    color: #909399;
    flex-shrink: 0;
  }
  .rag-source {
    font-weight: 500;
    color: #303133;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 200px;
  }
  .rag-score {
    color: #67c23a;
    font-size: 11px;
    margin-left: auto;
  }
  .rag-content {
    font-size: 12px;
    color: #606266;
    margin-top: 3px;
    line-height: 1.5;
    max-height: 60px;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    -webkit-box-orient: vertical;
  }
}

.msg-attachments {
  margin-bottom: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  .attachment-item {
    max-width: 180px;
    border-radius: 6px;
    overflow: hidden;
    border: 1px solid #ebeef5;
  }
  .msg-image {
    width: 120px;
    height: 120px;
    display: block;
    cursor: pointer;
  }
  .file-item {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px 10px;
    background: #f5f7fa;
    border-radius: 6px;
    font-size: 13px;
    color: #606266;
    .file-name {
      max-width: 120px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}

.msg-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 4px;
  min-height: 20px;
  .msg-time {
    font-size: 11px;
    color: #909399;
    margin-right: auto;
    line-height: 20px;
  }
  .msg-delete-btn {
    opacity: 0;
    transition: opacity 0.15s;
  }
}

.attachment-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
  .preview-item {
    position: relative;
    width: 80px;
    height: 80px;
    border-radius: 6px;
    overflow: hidden;
    border: 1px solid #ebeef5;
    background: #f5f7fa;
    .preview-image { width: 100%; height: 100%; }
    .preview-file {
      width: 100%;
      height: 100%;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 8px;
      font-size: 12px;
      color: #606266;
      text-align: center;
      .file-name {
        max-width: 100%;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        margin-top: 4px;
      }
    }
    .remove-icon {
      position: absolute;
      top: 2px;
      right: 2px;
      width: 18px;
      height: 18px;
      border-radius: 50%;
      background: rgba(0,0,0,0.5);
      color: #fff;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 12px;
      z-index: 2;
      &:hover { background: rgba(0,0,0,0.7); }
    }
  }
}

.upload-trigger { display: inline-flex; }

.thinking-switch {
  margin: 0 8px;
  :deep(.el-switch__label) {
    font-size: 12px;
    color: #606266;
  }
}

.cursor-blink { animation: blink 1s infinite; }

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

.chat-input {
  padding: 12px 16px;
  border-top: 1px solid #ebeef5;
  background: #fff;
}

.input-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
}

.session-id {
  font-size: 12px;
  color: #c0c4cc;
  margin-right: auto;
}

</style>
