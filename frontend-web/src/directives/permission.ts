import type { Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/user'

/** v-permission 指令：根据权限标识控制元素显示/隐藏 */
export const permission: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const { value } = binding
    const userStore = useUserStore()

    if (value && typeof value === 'string') {
      if (!userStore.hasPermission(value)) {
        el.parentNode?.removeChild(el)
      }
    } else if (value && Array.isArray(value)) {
      const hasAny = value.some((perm: string) => userStore.hasPermission(perm))
      if (!hasAny) {
        el.parentNode?.removeChild(el)
      }
    }
  }
}
