<template>
  <div class="dashboard-container">
    <!-- 欢迎横幅 -->
    <div class="welcome-banner">
      <div class="welcome-content">
        <h2 class="welcome-title">欢迎回来，{{ userStore.nickname }}</h2>
        <p class="welcome-desc">以下是平台运行概览，数据每 30 秒自动更新</p>
      </div>
      <div class="welcome-time">{{ currentTime }}</div>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="8" :md="4" v-for="card in statCards" :key="card.key">
        <el-card shadow="hover" class="stat-card" :body-style="{ padding: '20px' }">
          <div class="stat-content">
            <div class="stat-icon" :style="{ background: card.bg }">
              <el-icon :size="22"><component :is="card.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">
                <span v-if="!card.loading">{{ stats[card.key] }}</span>
                <span v-else class="stat-skeleton">--</span>
              </div>
              <div class="stat-label">{{ card.label }}</div>
            </div>
          </div>
          <div class="stat-footer">
            <span :style="{ color: card.color }">{{ card.trend }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 快捷入口 + 图表 -->
    <el-row :gutter="16">
      <el-col :xs="24" :lg="16">
        <el-card shadow="hover" class="section-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">快捷功能</span>
            </div>
          </template>
          <el-row :gutter="12">
            <el-col :xs="8" :sm="4" v-for="item in visibleQuickActions" :key="item.path">
              <div class="quick-item" @click="$router.push(item.path)">
                <div class="quick-icon" :style="{ background: item.bg }">
                  <el-icon :size="22" :color="item.color"><component :is="item.icon" /></el-icon>
                </div>
                <span class="quick-label">{{ item.label }}</span>
              </div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="8">
        <el-card shadow="hover" class="section-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">系统公告</span>
              <el-tag size="small" type="primary">{{ notices.length }}条</el-tag>
            </div>
          </template>
          <div class="notice-list">
            <div
              class="notice-item"
              v-for="(n, i) in notices"
              :key="i"
              @click="openNoticeDetail(n)"
            >
              <el-icon :color="n.color"><component :is="n.icon" /></el-icon>
              <span class="notice-text">{{ n.text }}</span>
              <span class="notice-time">{{ n.time }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 服务状态 + 架构信息 -->
    <el-row :gutter="16">
      <el-col :xs="24" :lg="14">
        <el-card shadow="hover" class="section-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">服务状态</span>
              <el-button text size="small" @click="refreshServices">
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
            </div>
          </template>
          <div class="service-grid">
            <div
              v-for="svc in services"
              :key="svc.name"
              class="service-item"
              :class="{ active: svc.status === 'running' }"
            >
              <div class="service-dot" :class="svc.status"></div>
              <div class="service-info">
                <span class="service-name">{{ svc.name }}</span>
                <span class="service-port">:{{ svc.port }}</span>
              </div>
              <span class="service-desc">{{ svc.desc }}</span>
              <el-tag :type="svc.status === 'running' ? 'success' : 'info'" size="small" effect="plain">
                {{ svc.status === 'running' ? '运行中' : '待检测' }}
              </el-tag>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="10">
        <el-card shadow="hover" class="section-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">技术栈</span>
            </div>
          </template>
          <div class="tech-stack">
            <div class="tech-item" v-for="tech in techStack" :key="tech.name">
              <div class="tech-tag" :style="{ borderColor: tech.color, color: tech.color }">
                {{ tech.name }}
              </div>
              <span class="tech-ver">{{ tech.version }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 公告详情弹窗 -->
    <el-dialog
      v-model="noticeDialogVisible"
      :title="currentNotice?.title || '公告详情'"
      width="600px"
      align-center
      destroy-on-close
    >
      <div class="notice-detail" v-if="currentNotice">
        <div class="notice-detail-meta">
          <el-tag size="small" :type="noticeTypeTag(currentNotice.noticeType)">
            {{ noticeTypeLabel(currentNotice.noticeType) }}
          </el-tag>
          <span class="notice-detail-publisher" v-if="currentNotice.publisherName">
            发布人：{{ currentNotice.publisherName }}
          </span>
          <span class="notice-detail-time">
            {{ formatFullTime(currentNotice.publishTime || currentNotice.createTime) }}
          </span>
        </div>
        <div class="notice-detail-content" v-html="currentNotice.content"></div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { getDashboardStats, getDashboardServices, getPublishedNotices } from '@/api/system'
import { getPendingCount } from '@/api/workflow'
import { useUserStore } from '@/stores/user'
import dayjs from 'dayjs'
import type { ServiceHealth, NoticeInfo } from '@/types'

const userStore = useUserStore()

const currentTime = ref(dayjs().format('YYYY年MM月DD日 dddd HH:mm'))
let timer: ReturnType<typeof setInterval>

const stats = reactive<Record<string, number>>({
  userCount: 0,
  clientCount: 0,
  systemActiveTokens: 0,
  subsystemActiveTokens: 0,
  pendingApprovals: 0,
})

const statCards = [
  { key: 'userCount', label: '平台用户', icon: 'User', bg: 'linear-gradient(135deg, #667eea, #764ba2)', color: '#667eea', trend: '实时统计', loading: false },
  { key: 'clientCount', label: '接入客户端', icon: 'Connection', bg: 'linear-gradient(135deg, #43e97b, #38f9d7)', color: '#43e97b', trend: '实时统计', loading: false },
  { key: 'systemActiveTokens', label: '系统活跃Token', icon: 'Key', bg: 'linear-gradient(135deg, #f093fb, #f5576c)', color: '#f5576c', trend: 'OAuth2实时', loading: false },
  { key: 'subsystemActiveTokens', label: '子系统活跃Token', icon: 'Link', bg: 'linear-gradient(135deg, #4facfe, #00f2fe)', color: '#4facfe', trend: '子系统实时', loading: false },
  { key: 'pendingApprovals', label: '待审批', icon: 'Bell', bg: 'linear-gradient(135deg, #ff9a44, #fc6076)', color: '#fc6076', trend: '需处理', loading: false },
]

const quickActions = [
  { path: '/system/user', icon: 'User', label: '用户管理', color: '#667eea', bg: '#f0f1ff', permission: 'system:user:list' },
  { path: '/auth/client', icon: 'Connection', label: '客户端管理', color: '#43e97b', bg: '#eefff6', permission: 'auth:client:edit' },
  { path: '/workflow/create', icon: 'EditPen', label: '发起审批', color: '#f093fb', bg: '#fff0fe', permission: 'workflow:definition:list' },
  { path: '/message/send', icon: 'Promotion', label: '发送消息', color: '#ff9a44', bg: '#fff7f0', permission: 'message:send' },
  { path: '/ai/search', icon: 'Search', label: 'AI搜索', color: '#409eff', bg: '#f0f6ff', permission: '' },
  { path: '/log/list', icon: 'Document', label: '日志查询', color: '#9c27b0', bg: '#f9f0ff', permission: 'log:list' },
]

/** 按权限过滤快捷入口 */
const visibleQuickActions = computed(() =>
  quickActions.filter(a => userStore.hasPermission(a.permission))
)

interface NoticeItem {
  icon: string
  color: string
  text: string
  time: string
  raw: NoticeInfo
}

const notices = ref<NoticeItem[]>([
  { icon: 'InfoFilled', color: '#409eff', text: '系统已升级至 V2.0 版本，新增 AI 智能体功能', time: '2小时前', raw: {} as NoticeInfo },
  { icon: 'WarningFilled', color: '#e6a23c', text: '请尽快完成客户端密钥轮转，提升安全性', time: '1天前', raw: {} as NoticeInfo },
  { icon: 'CircleCheckFilled', color: '#67c23a', text: '上周系统可用性达到 99.97%，运行稳定', time: '3天前', raw: {} as NoticeInfo },
])

const noticeDialogVisible = ref(false)
const currentNotice = ref<NoticeInfo | null>(null)

const services = ref([
  { name: 'Gateway', port: '8080', desc: 'API网关', status: 'unknown' },
  { name: 'Auth-Server', port: '9000', desc: 'OAuth2授权', status: 'unknown' },
  { name: 'System-Server', port: '9001', desc: '系统管理', status: 'unknown' },
  { name: 'Auth-Flow', port: '9001', desc: '工作流', status: 'unknown' },
  { name: 'Auth-Message', port: '9002', desc: '消息广播', status: 'unknown' },
  { name: 'AI-Agent', port: '9003', desc: 'AI智能体', status: 'unknown' },
  { name: 'Log-Server', port: '9009', desc: '日志服务', status: 'unknown' },
])

const techStack = [
  { name: 'Spring Auth Server', version: '1.2.4', color: '#6db33f' },
  { name: 'Spring Cloud', version: '2023.0.1', color: '#6db33f' },
  { name: 'Nacos', version: '2.3.2', color: '#1e88e5' },
  { name: 'RabbitMQ', version: '3.12', color: '#ff6600' },
  { name: 'Redis', version: '7.x', color: '#dc382d' },
  { name: 'MySQL', version: '8.0', color: '#4479a1' },
  { name: 'OpenAI + Lucene', version: 'RAG', color: '#10a37f' },
]

async function loadData() {
  const [dashboardResp, servicesResp, noticesResp, pendingResp] = await Promise.allSettled([
    getDashboardStats(),
    getDashboardServices(),
    getPublishedNotices(5),
    getPendingCount(userStore.userInfo?.username || 'admin'),
  ])

  // 从仪表盘 API 获取用户数、客户端数、系统/子系统Token数
  if (dashboardResp.status === 'fulfilled' && dashboardResp.value?.data) {
    const data = dashboardResp.value.data
    stats.userCount = data.userCount || data.totalUsers || 0
    stats.clientCount = data.clientCount || data.totalClients || 0
    stats.systemActiveTokens = data.systemActiveTokenCount || 0
    stats.subsystemActiveTokens = data.subsystemActiveTokenCount || data.activeTokenCount || 0
  }

  // 从工作流获取待审批数
  if (pendingResp.status === 'fulfilled' && pendingResp.value?.data) {
    stats.pendingApprovals = pendingResp.value.data
  }

  // 服务健康状态
  if (servicesResp.status === 'fulfilled' && Array.isArray(servicesResp.value?.data)) {
    const backendServices = servicesResp.value.data as ServiceHealth[]
    services.value = backendServices.map(s => ({
      name: s.name,
      port: s.port ? String(s.port) : '-',
      desc: serviceDesc(s.name),
      status: s.status,
    }))
  }

  // 已发布公告
  if (noticesResp.status === 'fulfilled' && Array.isArray(noticesResp.value?.data)) {
    const list = noticesResp.value.data as NoticeInfo[]
    notices.value = list.map(n => ({
      icon: noticeIcon(n.noticeType),
      color: noticeColor(n.priority),
      text: n.title,
      time: formatNoticeTime(n.publishTime || n.createTime),
      raw: n,
    }))
  }
}

function serviceDesc(name: string) {
  const map: Record<string, string> = {
    'Gateway': 'API网关',
    'Auth-Server': 'OAuth2授权',
    'System-Server': '系统管理',
    'Auth-Flow': '工作流',
    'Auth-Message': '消息广播',
    'AI-Agent': 'AI智能体',
    'Log-Server': '日志服务',
  }
  return map[name] || name
}

function noticeIcon(type?: string) {
  if (type === 'WARNING') return 'WarningFilled'
  if (type === 'MAINTAIN') return 'Tools'
  if (type === 'NOTICE') return 'BellFilled'
  return 'InfoFilled'
}

function noticeColor(priority?: number) {
  if (priority === 3) return '#f56c6c'
  if (priority === 2) return '#e6a23c'
  return '#409eff'
}

function formatNoticeTime(time?: string) {
  if (!time) return '刚刚'
  const d = dayjs(time)
  const now = dayjs()
  const diffMinutes = now.diff(d, 'minute')
  if (diffMinutes < 60) return diffMinutes <= 0 ? '刚刚' : `${diffMinutes}分钟前`
  const diffHours = now.diff(d, 'hour')
  if (diffHours < 24) return `${diffHours}小时前`
  const diffDays = now.diff(d, 'day')
  if (diffDays < 30) return `${diffDays}天前`
  return d.format('YYYY-MM-DD')
}

function formatFullTime(time?: string) {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm') : '-'
}

function openNoticeDetail(item: NoticeItem) {
  currentNotice.value = item.raw
  noticeDialogVisible.value = true
}

function noticeTypeLabel(type?: string) {
  const map: Record<string, string> = {
    ANNOUNCEMENT: '公告',
    NOTICE: '通知',
    WARNING: '警告',
    MAINTAIN: '维护',
  }
  return map[type || ''] || '通知'
}

function noticeTypeTag(type?: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  if (type === 'WARNING') return 'danger'
  if (type === 'MAINTAIN') return 'warning'
  if (type === 'NOTICE') return 'success'
  return 'info'
}

function refreshServices() {
  loadData()
}

onMounted(() => {
  loadData()
  timer = setInterval(() => {
    currentTime.value = dayjs().format('YYYY年MM月DD日 dddd HH:mm')
  }, 30000)
})

onUnmounted(() => {
  clearInterval(timer)
})
</script>

<style scoped lang="scss">
.dashboard-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

// 欢迎横幅
.welcome-banner {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  padding: 24px 28px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #fff;
}

.welcome-title {
  font-size: 22px;
  font-weight: 700;
  margin: 0 0 6px;
}

.welcome-desc {
  font-size: 13px;
  opacity: 0.8;
  margin: 0;
}

.welcome-time {
  font-size: 14px;
  opacity: 0.85;
  white-space: nowrap;
}

// 统计卡片
.stat-card {
  border-radius: 12px;
  overflow: hidden;
  transition: transform 0.2s, box-shadow 0.2s;

  &:hover {
    transform: translateY(-2px);
  }
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 14px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #1d2129;
  line-height: 1.2;
}

.stat-skeleton {
  display: inline-block;
  width: 40px;
  height: 26px;
  background: #f2f3f5;
  border-radius: 4px;
  color: transparent;
}

.stat-label {
  font-size: 13px;
  color: #86909c;
  margin-top: 2px;
}

.stat-footer {
  margin-top: 12px;
  font-size: 12px;
}

// 通用区块卡片
.section-card {
  border-radius: 12px;
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-weight: 600;
  font-size: 15px;
}

// 快捷入口
.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 18px 0;
  cursor: pointer;
  border-radius: 10px;
  transition: all 0.2s;

  &:hover {
    background: #f5f7fa;
    transform: translateY(-2px);
  }
}

.quick-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.quick-label {
  font-size: 12px;
  color: #4e5969;
  font-weight: 500;
}

// 公告
.notice-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.notice-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px dashed #f0f0f0;
  cursor: pointer;
  transition: background 0.2s;
  border-radius: 6px;
  margin: 0 -8px;
  padding-left: 8px;
  padding-right: 8px;

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background: #f5f7fa;
  }
}

.notice-text {
  flex: 1;
  font-size: 13px;
  color: #4e5969;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-time {
  font-size: 11px;
  color: #c9cdd4;
  white-space: nowrap;
}

// 服务状态网格
.service-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 8px;
}

.service-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f7f8fa;
  transition: background 0.2s;

  &.active {
    background: #f0fff4;
  }
}

.service-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;

  &.running { background: #67c23a; box-shadow: 0 0 6px rgba(103, 194, 58, 0.5); }
  &.unknown { background: #c9cdd4; }
}

.service-info {
  display: flex;
  align-items: baseline;
  gap: 2px;
  min-width: 120px;
}

.service-name {
  font-weight: 600;
  font-size: 13px;
  color: #1d2129;
}

.service-port {
  font-size: 11px;
  color: #c9cdd4;
}

.service-desc {
  flex: 1;
  font-size: 12px;
  color: #86909c;
}

// 技术栈
.tech-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.tech-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tech-tag {
  padding: 4px 10px;
  border: 1px solid;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
}

.tech-ver {
  font-size: 11px;
  color: #c9cdd4;
}

// 公告详情弹窗
.notice-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.notice-detail-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 13px;
  color: #86909c;
}

.notice-detail-content {
  font-size: 14px;
  line-height: 1.8;
  color: #1d2129;
  word-break: break-word;

  :deep(img) {
    max-width: 100%;
    height: auto;
    border-radius: 6px;
  }

  :deep(p) {
    margin: 0 0 12px;
  }

  :deep(a) {
    color: #409eff;
  }
}

// 响应式
@media (max-width: 768px) {
  .welcome-banner {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .welcome-title {
    font-size: 18px;
  }
}
</style>
