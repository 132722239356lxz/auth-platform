import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getUnreadCount } from '@/api/message'

const PREF_KEY = 'portal_preferences'

export interface TabItem {
  path: string
  title: string
  fullPath: string
}

function loadNotifyEnabled() {
  try {
    const prefs = localStorage.getItem(PREF_KEY)
    if (prefs) {
      const parsed = JSON.parse(prefs)
      return parsed.notifyEnabled !== false
    }
  } catch {
    // ignore
  }
  return true
}

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const unreadMessages = ref(0)
  const tabs = ref<TabItem[]>([])
  const notificationsEnabled = ref(loadNotifyEnabled())

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setNotificationsEnabled(enabled: boolean) {
    notificationsEnabled.value = enabled
  }

  function setUnreadMessages(count: number) {
    unreadMessages.value = count
  }

  /** 加载当前用户未读消息数 */
  async function loadUnreadCount(username: string) {
    if (!username || !notificationsEnabled.value) {
      setUnreadMessages(0)
      return
    }
    try {
      const res = await getUnreadCount(username)
      setUnreadMessages(res?.data || 0)
    } catch (error) {
      console.error('获取未读消息数失败:', error)
      setUnreadMessages(0)
    }
  }

  /** 添加/更新 Tab */
  function addTab(tab: TabItem) {
    const exists = tabs.value.find(t => t.path === tab.path)
    if (!exists) {
      tabs.value.push(tab)
    } else {
      // 更新 title（可能变化）
      exists.title = tab.title
    }
  }

  /** 关闭单个 Tab */
  function removeTab(path: string) {
    const idx = tabs.value.findIndex(t => t.path === path)
    if (idx !== -1) {
      tabs.value.splice(idx, 1)
    }
  }

  /** 关闭其他 Tab */
  function removeOtherTabs(path: string) {
    const tab = tabs.value.find(t => t.path === path)
    tabs.value = tab ? [tab] : []
  }

  /** 关闭所有 Tab */
  function removeAllTabs() {
    tabs.value = []
  }

  return {
    sidebarCollapsed,
    unreadMessages,
    tabs,
    notificationsEnabled,
    toggleSidebar,
    setNotificationsEnabled,
    setUnreadMessages,
    loadUnreadCount,
    addTab,
    removeTab,
    removeOtherTabs,
    removeAllTabs
  }
})
