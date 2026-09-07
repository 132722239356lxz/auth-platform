<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="sidebarWidth" class="sidebar">
      <SidebarMenu />
    </el-aside>

    <el-container>
      <!-- 顶部导航 -->
      <el-header class="navbar">
        <NavbarTop />
      </el-header>

      <!-- 页签导航 -->
      <TabsView />

      <!-- 主内容区 -->
      <el-main class="app-main">
        <router-view v-slot="{ Component }">
          <component :is="Component" />
        </router-view>
      </el-main>
    </el-container>

    <!-- 全局 AI 助手 -->
    <AiAssistant />
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import SidebarMenu from './components/Sidebar.vue'
import NavbarTop from './components/Navbar.vue'
import TabsView from './components/TabsView.vue'
import AiAssistant from '@/components/AiAssistant.vue'

const appStore = useAppStore()
const userStore = useUserStore()
const sidebarWidth = computed(() => appStore.sidebarCollapsed ? '64px' : '220px')

/** 用户登录后加载未读消息数 */
async function refreshUnreadCount() {
  const username = userStore.username
  if (username) {
    await appStore.loadUnreadCount(username)
  }
}

onMounted(refreshUnreadCount)
watch(() => userStore.username, refreshUnreadCount)
</script>

<style scoped lang="scss">
.layout-container {
  height: 100vh;
}

.sidebar {
  background-color: #304156;
  transition: width 0.28s;
  overflow: hidden;
}

.navbar {
  height: 56px;
  background-color: #fff;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  padding: 0 16px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.app-main {
  background-color: #f0f2f5;
  padding: 16px;
  overflow-y: auto;
}

.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: all 0.3s;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-20px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(20px);
}
</style>
