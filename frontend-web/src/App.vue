<template>
  <el-config-provider :locale="elementLocale">
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import { i18n, setI18nLanguage } from '@/i18n'

const PREF_KEY = 'portal_preferences'

function getStoredLanguage() {
  try {
    const prefs = localStorage.getItem(PREF_KEY)
    if (prefs) {
      const parsed = JSON.parse(prefs)
      if (parsed.language) return parsed.language
    }
  } catch {
    // ignore
  }
  return 'zh-CN'
}

// 同步初始化 vue-i18n 语言
setI18nLanguage(getStoredLanguage())

const elementLocale = computed(() => {
  return i18n.global.locale.value === 'en-US' ? en : zhCn
})

// 监听 localStorage 变化，实时切换 Element Plus 组件语言
window.addEventListener('storage', (e) => {
  if (e.key === PREF_KEY) {
    try {
      const parsed = JSON.parse(e.newValue || '{}')
      if (parsed.language) {
        setI18nLanguage(parsed.language)
      }
    } catch {
      // ignore
    }
  }
})
</script>

<style>
</style>
