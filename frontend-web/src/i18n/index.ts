import { createI18n } from 'vue-i18n'
import zhCN from '@/locales/zh-CN'
import enUS from '@/locales/en-US'

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

export const i18n = createI18n({
  legacy: false,
  locale: getStoredLanguage(),
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS
  }
})

export function setI18nLanguage(locale: string) {
  i18n.global.locale.value = locale as 'zh-CN' | 'en-US'
}

export default i18n
