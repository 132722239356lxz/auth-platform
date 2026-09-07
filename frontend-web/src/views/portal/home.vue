<template>
  <div class="portal-home">
    <!-- 欢迎横幅 -->
    <div class="welcome-banner">
      <div class="banner-content">
        <div class="banner-text">
          <h1>
            {{ greeting }}，{{ userStore.nickname }}
            <span class="wave">👋</span>
          </h1>
          <p>欢迎使用统一身份认证平台，您可以通过以下入口访问已授权的业务系统</p>
        </div>
        <div class="banner-illustration">
          <svg viewBox="0 0 200 100" fill="none">
            <rect x="20" y="20" width="50" height="50" rx="8" fill="rgba(255,255,255,0.2)"/>
            <rect x="80" y="10" width="50" height="50" rx="8" fill="rgba(255,255,255,0.15)"/>
            <rect x="140" y="30" width="50" height="50" rx="8" fill="rgba(255,255,255,0.1)"/>
            <circle cx="45" cy="45" r="8" fill="rgba(255,255,255,0.3)"/>
            <circle cx="105" cy="35" r="8" fill="rgba(255,255,255,0.25)"/>
            <circle cx="165" cy="55" r="8" fill="rgba(255,255,255,0.2)"/>
          </svg>
        </div>
      </div>
    </div>

    <div class="home-grid">
      <!-- 左侧：身份信息卡片 -->
      <div class="home-left">
        <!-- 用户身份卡片 -->
        <el-card class="identity-card" shadow="hover">
          <div class="identity-header">
            <el-avatar :size="64" :src="avatarUrl" :icon="UserFilled" class="identity-avatar" />
            <div class="identity-name">
              <h3>{{ userStore.nickname }}</h3>
              <el-tag size="small" :type="userStore.isAdmin ? 'danger' : 'success'" effect="light">
                {{ userStore.isAdmin ? '管理员' : '普通用户' }}
              </el-tag>
            </div>
          </div>
          <el-divider />
          <div class="identity-info">
            <div class="info-row">
              <span class="info-label">用户名</span>
              <span class="info-value">{{ userStore.userInfo?.username || '-' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">用户ID</span>
              <span class="info-value">{{ userStore.userInfo?.id || '-' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">所属部门</span>
              <span class="info-value">{{ userStore.userInfo?.deptName || '-' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">租户</span>
              <span class="info-value">{{ userStore.userInfo?.tenantId || '-' }}</span>
            </div>
            <div class="info-row" v-if="userStore.userInfo?.roleCodes?.length">
              <span class="info-label">角色</span>
              <span class="info-value">
                <el-tag
                  v-for="role in userStore.userInfo?.roleCodes"
                  :key="role"
                  size="small"
                  type="info"
                  effect="plain"
                  class="role-tag"
                >{{ role }}</el-tag>
              </span>
            </div>
          </div>
        </el-card>

        <!-- 快捷操作 -->
        <el-card class="quick-actions" shadow="hover">
          <template #header>
            <span>快捷操作</span>
          </template>
          <div class="actions-grid">
            <div class="action-item" @click="router.push('/portal/profile')">
              <div class="action-icon" style="background:#f0f6ff;color:#409eff">
                <el-icon><User /></el-icon>
              </div>
              <span>个人信息</span>
            </div>

            <div class="action-item" @click="router.push('/portal/settings')">
              <div class="action-icon" style="background:#fef0f0;color:#f56c6c">
                <el-icon><Setting /></el-icon>
              </div>
              <span>账号设置</span>
            </div>

            <div class="action-item" @click="router.push('/portal/apply')">
              <div class="action-icon" style="background:#f0f9eb;color:#67c23a">
                <el-icon><EditPen /></el-icon>
              </div>
              <span>发起申请</span>
            </div>

            <div class="action-item" @click="router.push('/portal/applications')">
              <div class="action-icon" style="background:#fdf6ec;color:#e6a23c">
                <el-icon><Document /></el-icon>
              </div>
              <span>我的申请</span>
            </div>
          </div>
        </el-card>
      </div>

      <!-- 右侧：子系统入口 -->
      <div class="home-right">
        <div class="subsystem-header">
          <h2>业务系统</h2>
          <el-radio-group
            v-model="activeCategory"
            size="small"
            fill="#409eff"
          >
            <el-radio-button
              v-for="cat in categories"
              :key="cat.code"
              :label="cat.code"
            >
              {{ cat.name }}
            </el-radio-button>
          </el-radio-group>
          <span class="subsystem-count">共 {{ displayList.length }} 个应用</span>
        </div>

        <el-alert
          v-if="loadingError"
          title="加载子系统列表失败，请检查网络连接"
          type="warning"
          show-icon
          :closable="false"
          style="margin-bottom: 16px;"
        />

        <div class="subsystem-grid" v-loading="loading">
          <div
            v-for="sub in displayList"
            :key="sub.subsystemId"
            class="subsystem-card"
            @click="openSubsystem(sub)"
          >
            <div class="card-icon">
              <img
                v-if="sub.iconUrl"
                :src="sub.iconUrl"
                :alt="sub.clientName"
                class="card-icon-img"
              />
              <div v-else class="card-icon-fallback" :style="{ background: iconBg(sub.subsystemId?.toString() || sub.clientName) }">
                <span class="card-icon-text">{{ (sub.clientName || '?').charAt(0) }}</span>
              </div>
            </div>
            <div class="card-info">
              <h4>{{ sub.clientName }}</h4>
              <p class="card-desc" v-if="sub.description">{{ sub.description }}</p>
              <p class="card-url" v-else>{{ sub.redirectUri }}</p>
              <div class="card-meta">
                <el-tag size="small" type="success" effect="light">
                  可访问
                </el-tag>
              </div>
            </div>
            <el-icon class="card-arrow"><ArrowRight /></el-icon>
          </div>

          <el-empty
            v-if="!loading && displayList.length === 0 && !loadingError"
            description="暂无已授权的业务系统"
            :image-size="80"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Setting, ArrowRight, UserFilled, EditPen, Document } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { getSubsystemNav, initSession } from '@/api/auth'
import type { SubsystemNavItem } from '@/types'

const router = useRouter()
const userStore = useUserStore()

const avatarUrl = computed(() => userStore.userInfo?.avatar || '')
const loading = ref(true)
const loadingError = ref(false)
const subsystemList = ref<SubsystemNavItem[]>([])
const activeCategory = ref('web')

const categories = computed(() => {
  const codes = new Set<string>()
  subsystemList.value.forEach(item => {
    codes.add(item.subsystemCode || 'other')
  })
  return Array.from(codes).map(code => ({ code, name: code }))
})

const displayList = computed(() => {
  return subsystemList.value.filter(item => (item.subsystemCode || 'other') === activeCategory.value)
})

const greeting = computedGreeting()

function computedGreeting(): string {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 9) return '早上好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
}

const COLOR_PALETTE = [
  'linear-gradient(135deg, #667eea, #764ba2)',
  'linear-gradient(135deg, #f093fb, #f5576c)',
  'linear-gradient(135deg, #4facfe, #00f2fe)',
  'linear-gradient(135deg, #43e97b, #38f9d7)',
  'linear-gradient(135deg, #fa709a, #fee140)',
  'linear-gradient(135deg, #a18cd1, #fbc2eb)',
  'linear-gradient(135deg, #fccb90, #d57eeb)',
  'linear-gradient(135deg, #667eea, #409eff)',
  'linear-gradient(135deg, #ff9a9e, #fad0c4)',
  'linear-gradient(135deg, #a1c4fd, #c2e9fb)',
]
function iconBg(seed: string) {
  let hash = 0
  for (let i = 0; i < seed.length; i++) {
    hash = seed.charCodeAt(i) + ((hash << 5) - hash)
  }
  return COLOR_PALETTE[Math.abs(hash) % COLOR_PALETTE.length]
}

async function openSubsystem(sub: SubsystemNavItem) {
  if (!sub.ssoEnabled || !sub.authorizeEndpoint) {
    ElMessage.warning('该子系统未启用 SSO 授权')
    return
  }
  const redirectUri = sub.redirectUri || (sub.redirectUris && sub.redirectUris[0])
  if (!redirectUri) {
    ElMessage.warning('该子系统未配置回调地址')
    return
  }
  // state 传递子系统的回调地址，callback 页面将用它作为 exchange-code 的 redirectUri
  const params = new URLSearchParams({
    response_type: 'code',
    client_id: sub.clientId,
    redirect_uri: redirectUri,
    scope: (sub.scopes || []).join(' '),
    state: redirectUri
  })

  // Step 1: 初始化 Session（Axios 携带 Bearer token → 后端创建 HttpSession → 浏览器存储 AUTH_SESSION cookie）
  try {
    await initSession()
  } catch {
    ElMessage.error('Session 初始化失败，请重新登录')
    return
  }

  // Step 2: 打开 OAuth2 授权页面（浏览器自动携带 AUTH_SESSION cookie → 后端识别已登录用户 → 签发授权码）
  // 确保使用 127.0.0.1 而非 localhost（同一 hostname 才能共享 cookie）
  const origin = window.location.origin.replace('localhost', '127.0.0.1')
  window.open(`${origin}${sub.authorizeEndpoint}?${params.toString()}`, '_blank')
}

async function loadSubsystems() {
  loading.value = true
  loadingError.value = false
  try {
    const res = await getSubsystemNav()
    subsystemList.value = res?.data || []
  } catch {
    loadingError.value = true
    subsystemList.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadSubsystems()
})
</script>

<style scoped lang="scss">
.portal-home {
  max-width: 1200px;
  margin: 0 auto;
}

.welcome-banner {
  background: linear-gradient(135deg, #1677ff 0%, #409eff 50%, #69b1ff 100%);
  border-radius: 16px;
  padding: 32px 36px;
  margin-bottom: 24px;
  color: #fff;
  overflow: hidden;
  position: relative;
}

.banner-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  z-index: 1;
}

.banner-text {
  h1 {
    font-size: 24px;
    font-weight: 700;
    margin: 0 0 8px;
    .wave {
      display: inline-block;
      animation: wave-anim 1.5s ease-in-out infinite;
    }
  }
  p {
    margin: 0;
    font-size: 14px;
    opacity: 0.85;
  }
}

.banner-illustration {
  flex-shrink: 0;
  svg { width: 160px; height: 80px; }
}

.home-grid {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 24px;
  align-items: start;
}

.home-left {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.identity-card {
  border-radius: 12px;

  .identity-header {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .identity-avatar {
    background: linear-gradient(135deg, #409eff, #1677ff);
    font-size: 24px;
  }

  .identity-name {
    h3 { margin: 0 0 6px; font-size: 18px; }
  }

  .identity-info {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .info-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 13px;

    .info-label { color: #909399; }
    .info-value { color: #303133; font-weight: 500; }
  }

  .role-tag { margin-left: 4px; }
}

.quick-actions {
  border-radius: 12px;
}

.actions-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 12px 8px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.2s;
  font-size: 13px;
  color: #606266;

  &:hover { background: #f5f7fa; }

  .action-icon {
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 10px;
    font-size: 18px;
  }
}

.home-right {
  min-width: 0;
}

.subsystem-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;

  h2 {
    margin: 0;
    font-size: 20px;
    color: #303133;
  }
  .subsystem-count {
    font-size: 13px;
    color: #909399;
    margin-left: auto;
  }
  .el-radio-group {
    order: 3;
    flex: 1 1 100%;
  }
  @media (min-width: 768px) {
    .el-radio-group {
      order: 0;
      flex: 0 0 auto;
    }
    .subsystem-count {
      margin-left: 0;
    }
  }
}

.subsystem-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.subsystem-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: all 0.25s;
  border: 1px solid transparent;

  &:hover {
    border-color: #d9ecff;
    box-shadow: 0 4px 16px rgba(64, 158, 255, 0.12);
    transform: translateY(-2px);
  }

  .card-icon {
    width: 52px;
    height: 52px;
    border-radius: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    overflow: hidden;
  }

  .card-icon-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .card-icon-fallback {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .card-icon-text {
    color: #fff;
    font-size: 22px;
    font-weight: 700;
  }

  .card-info {
    flex: 1;
    min-width: 0;

    h4 {
      margin: 0 0 4px;
      font-size: 15px;
      color: #303133;
    }

    .card-desc {
      margin: 0 0 8px;
      font-size: 12px;
      color: #909399;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .card-url {
      margin: 0 0 8px;
      font-size: 11px;
      color: #c0c4cc;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }

  .card-arrow {
    color: #c0c4cc;
    font-size: 16px;
    transition: transform 0.2s;
  }
  &:hover .card-arrow { transform: translateX(4px); color: #409eff; }
}

@keyframes wave-anim {
  0%, 100% { transform: rotate(0); }
  25% { transform: rotate(15deg); }
  75% { transform: rotate(-10deg); }
}

@media (max-width: 900px) {
  .home-grid {
    grid-template-columns: 1fr;
  }
  .banner-illustration { display: none; }
  .subsystem-grid {
    grid-template-columns: 1fr;
  }
}
</style>
