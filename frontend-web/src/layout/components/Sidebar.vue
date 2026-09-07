<template>
  <div class="sidebar-wrapper">
    <!-- Logo -->
    <div class="logo">
      <img v-if="!collapsed" src="" alt="" class="logo-img" style="display:none" />
      <h1 v-if="!collapsed" class="logo-title">统一授权中台</h1>
      <h1 v-else class="logo-title-mini">Auth</h1>
    </div>

    <!-- 菜单 (API 动态加载) -->
    <el-scrollbar>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        :unique-opened="true"
        router
      >
        <template v-for="item in sidebarItems" :key="item.path">
          <!-- 单层菜单（目录无子菜单 或 顶层叶子菜单） -->
          <el-menu-item v-if="item.isLeaf" :index="item.path">
            <el-icon v-if="item.icon">
              <component :is="item.icon" />
            </el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>

          <!-- 多层菜单（目录 + 子菜单） -->
          <el-sub-menu v-else :index="item.path">
            <template #title>
              <el-icon v-if="item.icon">
                <component :is="item.icon" />
              </el-icon>
              <span>{{ item.title }}</span>
            </template>
            <el-menu-item
              v-for="child in item.children"
              :key="child.path"
              :index="child.path"
            >
              <el-icon v-if="child.icon">
                <component :is="child.icon" />
              </el-icon>
              <span>{{ child.title }}</span>
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import type { MenuInfo } from '@/types'

const route = useRoute()
const appStore = useAppStore()
const userStore = useUserStore()

const collapsed = computed(() => appStore.sidebarCollapsed)
const activeMenu = computed(() => route.path)

// ==================== API 菜单 → 侧边栏渲染数据 ====================

interface SidebarItem {
  path: string
  title: string
  icon?: string
  isLeaf: boolean
  children?: SidebarLeafItem[]
}

interface SidebarLeafItem {
  path: string
  title: string
  icon?: string
}

/** 将 API 菜单树转换为侧边栏渲染结构（只保留目录/菜单，过滤按钮类型） */
const sidebarItems = computed<SidebarItem[]>(() => {
  const menus = userStore.menus
  if (!menus || menus.length === 0) return []
  return menus
    .filter(m => m.enabled !== false && m.menuType !== 2)
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    .map(item => convertMenuItem(item))
    .filter(Boolean) as SidebarItem[]
})

function convertMenuItem(item: MenuInfo): SidebarItem | null {
  if (item.menuType === 2) return null // 过滤按钮

  // 获取可见子菜单（排除按钮和禁用项）
  const visibleChildren = (item.children || [])
    .filter(c => c.enabled !== false && c.menuType !== 2)
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))

  if (visibleChildren.length > 0) {
    // 目录：取第一个可见子菜单的 path 作为父级 path（兼容 el-menu router 模式）
    return {
      path: item.path || visibleChildren[0].path || '',
      title: item.menuName,
      icon: validIcon(item.icon),
      isLeaf: false,
      children: visibleChildren.map(c => ({
        path: c.path || '',
        title: c.menuName,
        icon: validIcon(c.icon),
      })),
    }
  }

  // 叶子菜单
  return {
    path: item.path || '',
    title: item.menuName,
    icon: validIcon(item.icon),
    isLeaf: true,
  }
}

/** 校验图标名是否为已注册的 Element Plus 图标组件 */
function validIcon(icon?: string): string | undefined {
  if (!icon) return undefined
  // Element Plus 图标名称形式如: Setting, User, Odometer, Menu 等
  // 简单排除明显无效的图标名
  return icon.length <= 30 ? icon : undefined
}
</script>

<style scoped lang="scss">
.sidebar-wrapper {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #2b3a4d;
  flex-shrink: 0;
}

.logo-title {
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  white-space: nowrap;
}

.logo-title-mini {
  color: #fff;
  font-size: 18px;
  font-weight: 700;
}

.el-scrollbar {
  flex: 1;
}

::deep(.el-menu) {
  border-right: none;
}
</style>
