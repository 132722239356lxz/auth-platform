<template>
  <div class="tabs-view" v-if="tabs.length > 0">
    <div class="tabs-scroll" ref="scrollRef">
      <div
        v-for="tab in tabs"
        :key="tab.path"
        class="tab-item"
        :class="{ active: tab.path === activePath }"
        @click="navigateTo(tab)"
        @contextmenu.prevent="openContextMenu($event, tab)"
      >
        <span class="tab-title">{{ tab.title }}</span>
        <el-icon class="tab-close" @click.stop="closeTab(tab)">
          <Close />
        </el-icon>
      </div>
    </div>

    <!-- 右键菜单 -->
    <Teleport to="body">
      <div
        v-if="contextMenu.visible"
        class="context-menu"
        :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
      >
        <div class="menu-item" @click="closeCurrentTab">
          <el-icon><Close /></el-icon>
          <span>关闭当前</span>
        </div>
        <div class="menu-item" @click="closeOtherTabs">
          <el-icon><CircleClose /></el-icon>
          <span>关闭其他</span>
        </div>
        <div class="menu-item" @click="closeAllTabs">
          <el-icon><FolderDelete /></el-icon>
          <span>关闭所有</span>
        </div>
      </div>
    </Teleport>

    <!-- 点击空白关闭右键菜单 -->
    <div v-if="contextMenu.visible" class="context-mask" @click="contextMenu.visible = false" @contextmenu.prevent="contextMenu.visible = false" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore, type TabItem } from '@/stores/app'
import { Close, CircleClose, FolderDelete } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

const tabs = computed(() => appStore.tabs)
const activePath = computed(() => route.path)
const scrollRef = ref<HTMLElement>()

const contextMenu = ref({
  visible: false,
  x: 0,
  y: 0,
  tab: null as TabItem | null,
})

// 监听路由变化，自动添加 Tab（排除登录页等）
watch(
  () => route.path,
  (path) => {
    if (path === '/adminLogin' || path === '/login' || path === '/login/callback' || path === '/register') return
    const title = (route.meta.title as string) || path
    appStore.addTab({
      path: route.path,
      title,
      fullPath: route.fullPath,
    })
  },
  { immediate: true }
)

function navigateTo(tab: TabItem) {
  router.push(tab.fullPath || tab.path)
}

function closeTab(tab: TabItem) {
  const idx = appStore.tabs.findIndex(t => t.path === tab.path)
  appStore.removeTab(tab.path)

  // 如果关闭的是当前页，导航到相邻 Tab
  if (tab.path === route.path) {
    const remaining = appStore.tabs
    if (remaining.length > 0) {
      const target = remaining[Math.min(idx, remaining.length - 1)]
      router.push(target.fullPath || target.path)
    } else {
      router.push('/dashboard')
    }
  }
}

function openContextMenu(e: MouseEvent, tab: TabItem) {
  contextMenu.value = {
    visible: true,
    x: e.clientX,
    y: e.clientY,
    tab,
  }
}

function closeCurrentTab() {
  if (contextMenu.value.tab) {
    closeTab(contextMenu.value.tab)
  }
  contextMenu.value.visible = false
}

function closeOtherTabs() {
  if (contextMenu.value.tab) {
    appStore.removeOtherTabs(contextMenu.value.tab.path)
    router.push(contextMenu.value.tab.fullPath || contextMenu.value.tab.path)
  }
  contextMenu.value.visible = false
}

function closeAllTabs() {
  appStore.removeAllTabs()
  router.push('/dashboard')
  contextMenu.value.visible = false
}

onMounted(() => {
  document.addEventListener('click', () => {
    contextMenu.value.visible = false
  })
})
</script>

<style scoped lang="scss">
.tabs-view {
  position: relative;
  height: 36px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 0 8px;
  user-select: none;
}

.tabs-scroll {
  display: flex;
  align-items: center;
  height: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  white-space: nowrap;

  &::-webkit-scrollbar {
    height: 2px;
  }
  &::-webkit-scrollbar-thumb {
    background: #c0c4cc;
    border-radius: 2px;
  }
}

.tab-item {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 10px 0 12px;
  margin-right: 4px;
  font-size: 12px;
  color: #606266;
  background: #f0f2f5;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;

  &:hover {
    color: #409eff;
    background: #ecf5ff;
  }

  &.active {
    color: #409eff;
    background: #ecf5ff;
    font-weight: 500;
  }
}

.tab-title {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tab-close {
  font-size: 12px;
  margin-left: 6px;
  border-radius: 50%;
  padding: 2px;
  transition: all 0.2s;

  &:hover {
    color: #fff;
    background: #c0c4cc;
  }
}

.tab-item.active .tab-close:hover {
  color: #fff;
  background: #409eff;
}
</style>

<style scoped>
/* 全局右键菜单（不能 scoped） */
.context-mask {
  position: fixed;
  inset: 0;
  z-index: 999;
}
</style>

<style>
.context-menu {
  position: fixed;
  z-index: 1000;
  min-width: 140px;
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
  padding: 4px 0;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    color: #409eff;
    background: #ecf5ff;
  }
}
</style>
