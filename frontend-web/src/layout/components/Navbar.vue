<template>
  <div class="navbar-wrapper">
    <!-- 左侧：折叠按钮 + 面包屑 -->
    <div class="navbar-left">
      <el-icon class="collapse-btn" @click="appStore.toggleSidebar()">
        <Fold v-if="!appStore.sidebarCollapsed" />
        <Expand v-else />
      </el-icon>

      <!-- 面包屑 — 完整层级 -->
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/dashboard' }">{{ $t('navbar.home') }}</el-breadcrumb-item>
        <el-breadcrumb-item
          v-for="item in breadcrumbs"
          :key="item.path"
          :to="item.clickable ? { path: item.path } : undefined"
        >
          {{ item.title }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- 右侧：用户信息 -->
    <div class="navbar-right">
      <el-tooltip v-permission="'message:inbox'" :content="$t('navbar.notifications')" placement="bottom">
        <el-badge :value="appStore.unreadMessages" :hidden="appStore.unreadMessages === 0" class="badge-item">
          <el-icon class="action-icon" @click="$router.push('/message/inbox')">
            <Bell />
          </el-icon>
        </el-badge>
      </el-tooltip>

      <el-dropdown trigger="click" @command="handleCommand">
        <div class="user-info">
          <el-avatar :size="32" class="user-avatar">
            {{ userStore.nickname?.charAt(0)?.toUpperCase() || 'U' }}
          </el-avatar>
          <span class="user-name">{{ userStore.nickname || userStore.username || $t('navbar.username') }}</span>
          <el-icon><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon>
              {{ $t('navbar.profile') }}
            </el-dropdown-item>
            <el-dropdown-item command="settings">
              <el-icon><Setting /></el-icon>
              {{ $t('navbar.settings') }}
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon>
              {{ $t('navbar.logout') }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

/** 从当前路由的 matched 中提取面包屑层级 */
interface BreadcrumbItem {
  title: string
  path: string
  clickable: boolean
}

const breadcrumbs = computed<BreadcrumbItem[]>(() => {
  const crumbs: BreadcrumbItem[] = []
  for (const record of route.matched) {
    // 跳过 Layout 和隐藏路由
    if (record.meta?.hidden && record.path !== route.path) continue
    const title = record.meta?.title as string
    if (!title) continue
    const isCurrent = record.path === route.path
    crumbs.push({
      title,
      path: record.path || '/',
      clickable: !isCurrent && record.path !== '/',
    })
  }
  return crumbs
})

function handleCommand(command: string) {
  switch (command) {
    case 'profile':
      router.push('/profile')
      break
    case 'settings':
      router.push('/settings')
      break
    case 'logout':
      ElMessageBox.confirm(t('common.logoutTip'), t('common.confirm'), {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'warning'
      }).then(() => {
        userStore.logout()
        router.push('/adminLogin')
      }).catch(() => {})
      break
  }
}
</script>

<style scoped lang="scss">
.navbar-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  height: 100%;
}

.navbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #5a5e66;

  &:hover {
    color: #409eff;
  }
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.action-icon {
  font-size: 18px;
  cursor: pointer;
  color: #5a5e66;

  &:hover {
    color: #409eff;
  }
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.user-avatar {
  background-color: #409eff;
  color: #fff;
  font-weight: 600;
}

.user-name {
  font-size: 14px;
  color: #303133;
}
</style>
