<template>
  <div class="portal-layout">
    <!-- 顶部导航 -->
    <header class="portal-header">
      <div class="header-inner">
        <div class="header-left" @click="router.push('/portal/home')">
          <div class="header-logo">
            <svg viewBox="0 0 32 32" fill="none" width="32" height="32">
              <rect x="2" y="8" width="28" height="18" rx="5" stroke="white" stroke-width="1.8" fill="rgba(255,255,255,0.15)"/>
              <circle cx="12" cy="18" r="2" fill="white"/>
              <circle cx="20" cy="18" r="2" fill="white"/>
              <path d="M12 23h8" stroke="white" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
          </div>
          <span class="header-title">统一身份认证平台</span>
        </div>

        <div class="header-right">
          <!-- 消息提醒（点击跳转门户站内信，不跳转后台系统） -->
          <el-tooltip v-if="appStore.notificationsEnabled" content="站内信" placement="bottom">
            <el-badge :value="appStore.unreadMessages" :hidden="!appStore.unreadMessages" :max="99">
              <div class="header-action" @click="router.push('/portal/messages')">
                <el-icon :size="20"><Bell /></el-icon>
              </div>
            </el-badge>
          </el-tooltip>

          <!-- 用户信息 -->
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-area">
              <el-avatar :size="34" :src="avatarUrl" :icon="UserFilled" class="user-avatar" />
              <span class="user-name">{{ userStore.nickname }}</span>
              <el-icon class="user-arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item command="settings">
                  <el-icon><Setting /></el-icon>账号设置
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>

    <!-- 主内容 -->
    <main class="portal-main">
      <router-view v-slot="{ Component }">
        <transition name="portal-fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <!-- 页脚 -->
    <footer class="portal-footer">
      <p>Copyright &copy; 2026 统一授权中台 · All Rights Reserved</p>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, ArrowDown, User, SwitchButton, UserFilled, Setting } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

const avatarUrl = computed(() => userStore.userInfo?.avatar || '')

onMounted(() => {
  const username = userStore.username
  if (username) appStore.loadUnreadCount(username)

  // 每 30 秒轮询一次未读消息数
  timer = window.setInterval(() => {
    if (!appStore.notificationsEnabled) return
    const name = userStore.username
    if (name) appStore.loadUnreadCount(name)
  }, 30000)
})
onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
    timer = undefined
  }
})
watch(() => userStore.username, (name) => {
  if (name) appStore.loadUnreadCount(name)
})
watch(() => appStore.notificationsEnabled, (enabled) => {
  if (!enabled) {
    appStore.setUnreadMessages(0)
  } else {
    const name = userStore.username
    if (name) appStore.loadUnreadCount(name)
  }
})

let timer: number | undefined

function handleCommand(cmd: string) {
  switch (cmd) {
    case 'profile':
      router.push('/portal/profile')
      break
    case 'settings':
      router.push('/portal/settings')
      break
    case 'logout':
      userStore.logout()
      router.push('/login')
      break
  }
}
</script>

<style scoped lang="scss">
.portal-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: #f5f7fa;
}

.portal-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: linear-gradient(135deg, #1677ff, #409eff);
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.2);
}

.header-inner {
  max-width: 1280px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  user-select: none;
}

.header-title {
  color: #fff;
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 1px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-action {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.85);
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: rgba(255, 255, 255, 0.15);
    color: #fff;
  }
}

.user-area {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px 4px 4px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.2s;
  margin-left: 4px;

  &:hover { background: rgba(255, 255, 255, 0.12); }
}

.user-avatar {
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
  font-size: 16px;
}

.user-name {
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-arrow {
  color: rgba(255, 255, 255, 0.6);
  font-size: 12px;
}

.portal-main {
  flex: 1;
  max-width: 1280px;
  width: 100%;
  margin: 0 auto;
  padding: 24px;
}

.portal-footer {
  text-align: center;
  padding: 20px;
  color: #c0c4cc;
  font-size: 12px;
  border-top: 1px solid #ebeef5;

  p { margin: 0; }
}

.portal-fade-enter-active,
.portal-fade-leave-active {
  transition: opacity 0.25s ease;
}
.portal-fade-enter-from,
.portal-fade-leave-to {
  opacity: 0;
}

@media (max-width: 768px) {
  .header-title { display: none; }
  .user-name { display: none; }
  .portal-main { padding: 16px; }
}
</style>
