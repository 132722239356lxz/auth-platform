import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import { useUserStore } from '@/stores/user'
import { getAccessToken } from '@/utils/auth'
import { logRoute } from '@/utils/devLog'

// 布局
const Layout = () => import('@/layout/index.vue')
const PortalLayout = () => import('@/layout/PortalLayout.vue')

// 静态路由 —— 管理后台 & 用户门户 完全分离
export const staticRoutes: RouteRecordRaw[] = [
  // ==================== 公共路由（无布局） ====================
  {
    path: '/adminLogin',
    name: 'AdminLogin',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '管理后台登录', hidden: true }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/portal/login.vue'),
    meta: { title: '用户登录', hidden: true }
  },
  {
    path: '/login/callback',
    name: 'LoginCallback',
    component: () => import('@/views/login/callback.vue'),
    meta: { title: 'OAuth2 回调', hidden: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/login/register.vue'),
    meta: { title: '用户注册', hidden: true }
  },

  // ==================== 管理后台 ====================
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '仪表盘', icon: 'Odometer' }
      }
    ]
  },
  {
    path: '/profile',
    component: Layout,
    meta: { title: '个人中心', hidden: true },
    children: [
      {
        path: '',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人中心', hidden: true }
      }
    ]
  },
  {
    path: '/settings',
    component: Layout,
    meta: { title: '系统设置', hidden: true },
    children: [
      {
        path: '',
        name: 'Settings',
        component: () => import('@/views/settings/index.vue'),
        meta: { title: '系统设置', hidden: true }
      }
    ]
  },
  {
    path: '/system',
    component: Layout,
    redirect: '/system/user',
    meta: { title: '系统管理', icon: 'Setting' },
    children: [
      {
        path: 'user',
        name: 'SystemUser',
        component: () => import('@/views/system/user.vue'),
        meta: { title: '用户管理', icon: 'User', permission: 'system:user:list' }
      },
      {
        path: 'role',
        name: 'SystemRole',
        component: () => import('@/views/system/role.vue'),
        meta: { title: '角色管理', icon: 'UserFilled', permission: 'system:role:list' }
      },
      {
        path: 'menu',
        name: 'SystemMenu',
        component: () => import('@/views/system/menu.vue'),
        meta: { title: '菜单管理', icon: 'Menu', permission: 'system:menu:list' }
      },
      {
        path: 'dept',
        name: 'SystemDept',
        component: () => import('@/views/system/dept.vue'),
        meta: { title: '部门管理', icon: 'OfficeBuilding', permission: 'system:dept:list' }
      },
      {
        path: 'dict',
        name: 'SystemDict',
        component: () => import('@/views/system/dict.vue'),
        meta: { title: '数据字典', icon: 'Document', permission: 'system:dict:list' }
      },
      {
        path: 'notice',
        name: 'SystemNotice',
        component: () => import('@/views/system/notice.vue'),
        meta: { title: '公告管理', icon: 'Bell', permission: 'system:notice:list' }
      },
      {
        path: 'provider',
        name: 'SystemAiProvider',
        component: () => import('@/views/system/provider.vue'),
        meta: { title: 'AI 供应商', icon: 'Cpu', permission: 'system:ai-provider:list' }
      },
      {
        path: 'invoke-log',
        name: 'SystemAiInvokeLog',
        component: () => import('@/views/system/ai-invoke-log.vue'),
        meta: { title: 'AI 调用记录', icon: 'Document', permission: 'system:ai-invoke-log:list' }
      }
    ]
  },
  {
    path: '/auth',
    component: Layout,
    redirect: '/auth/client',
    meta: { title: '授权管理', icon: 'Key' },
    children: [
      {
        path: 'client',
        name: 'AuthClient',
        component: () => import('@/views/auth/client.vue'),
        meta: { title: '客户端管理', icon: 'Connection' }
      },
      {
        path: 'audit',
        name: 'AuthAudit',
        component: () => import('@/views/auth/audit.vue'),
        meta: { title: '授权审计', icon: 'Document' }
      },
      {
        path: 'subsystem',
        name: 'AuthSubsystem',
        component: () => import('@/views/auth/subsystem.vue'),
        meta: { title: '子系统Token', icon: 'Link' }
      }
    ]
  },
  {
    path: '/workflow',
    component: Layout,
    redirect: '/workflow/list',
    meta: { title: '工作流审批', icon: 'Tickets' },
    children: [
      {
        path: 'list',
        name: 'WorkflowList',
        component: () => import('@/views/workflow/list.vue'),
        meta: { title: '审批列表', icon: 'List', permission: 'workflow:instance:list' }
      },
      {
        path: 'create',
        name: 'WorkflowCreate',
        component: () => import('@/views/workflow/create.vue'),
        meta: { title: '发起申请', icon: 'EditPen', permission: 'workflow:instance:add' }
      },
      {
        path: 'detail/:id',
        name: 'WorkflowDetail',
        component: () => import('@/views/workflow/detail.vue'),
        meta: { title: '流程详情', hidden: true }
      },
      {
        path: 'definition',
        name: 'WorkflowDefinition',
        component: () => import('@/views/workflow/definition.vue'),
        meta: { title: '流程定义', icon: 'SetUp', permission: 'workflow:definition:list' }
      },
      {
        path: 'form',
        name: 'WorkflowForm',
        component: () => import('@/views/workflow/form.vue'),
        meta: { title: '表单维护', icon: 'Document', permission: 'workflow:form:list' }
      }
    ]
  },
  {
    path: '/message',
    component: Layout,
    redirect: '/message/inbox',
    meta: { title: '消息中心', icon: 'Bell' },
    children: [
      {
        path: 'inbox',
        name: 'MessageInbox',
        component: () => import('@/views/message/inbox.vue'),
        meta: { title: '收件箱', icon: 'Message', permission: 'message:inbox' }
      },
      {
        path: 'send',
        name: 'MessageSend',
        component: () => import('@/views/message/send.vue'),
        meta: { title: '发送消息', icon: 'Promotion', permission: 'message:send' }
      },
      {
        path: 'record',
        name: 'MessageRecord',
        component: () => import('@/views/message/record.vue'),
        meta: { title: '发送记录', icon: 'DocumentCopy', permission: 'message:inbox' }
      },
      {
        path: 'template',
        name: 'MessageTemplate',
        component: () => import('@/views/message/template.vue'),
        meta: { title: '消息模板', icon: 'Document', permission: 'message:template:list' }
      },
      {
        path: 'events',
        name: 'MessageEvents',
        component: () => import('@/views/message/events.vue'),
        meta: { title: '子系统事件', icon: 'Connection', permission: 'message:event:list' }
      },
      {
        path: 'incident',
        name: 'MessageIncident',
        component: () => import('@/views/message/incident.vue'),
        meta: { title: '异常反馈', icon: 'WarningFilled', permission: 'message:incident:list' }
      }
    ]
  },
  {
    path: '/ai',
    component: Layout,
    redirect: '/ai/search',
    meta: { title: 'AI智能应用', icon: 'MagicStick' },
    children: [
      {
        path: 'search',
        name: 'AISearch',
        component: () => import('@/views/ai/search.vue'),
        meta: { title: '智能搜索', icon: 'Search', permission: 'ai:search:list' }
      },
      {
        path: 'knowledge',
        name: 'AIKnowledge',
        component: () => import('@/views/ai/knowledge.vue'),
        meta: { title: '知识库管理', icon: 'Collection', permission: 'ai:knowledge:list' }
      },
      {
        path: 'chat',
        name: 'AIChat',
        component: () => import('@/views/ai/chat.vue'),
        meta: { title: 'AI助手', icon: 'ChatDotRound', permission: 'ai:chat:list' }
      },
      {
        path: 'analysis',
        name: 'AIAnalysis',
        component: () => import('@/views/ai/analysis.vue'),
        meta: { title: '数据预警', icon: 'Warning', permission: 'ai:analysis:list' }
      }
    ]
  },
  {
    path: '/log',
    component: Layout,
    redirect: '/log/list',
    meta: { title: '日志管理', icon: 'Files' },
    children: [
      {
        path: 'list',
        name: 'LogList',
        component: () => import('@/views/log/list.vue'),
        meta: { title: '日志查询', icon: 'Document', permission: 'log:list' }
      },
      {
        path: 'analysis',
        name: 'LogAnalysis',
        component: () => import('@/views/log/analysis.vue'),
        meta: { title: 'AI日志分析', icon: 'Monitor', permission: 'log:analysis:list' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404', hidden: true }
  },

  // ==================== 用户门户（独立布局，不与后台互通） ====================
  {
    path: '/portal',
    component: PortalLayout,
    redirect: '/portal/home',
    meta: { title: '用户门户', hidden: true },
    children: [
      {
        path: 'home',
        name: 'PortalHome',
        component: () => import('@/views/portal/home.vue'),
        meta: { title: '工作台', hidden: true }
      },
      {
        path: 'profile',
        name: 'PortalProfile',
        component: () => import('@/views/portal/profile.vue'),
        meta: { title: '个人中心', hidden: true }
      },
      {
        path: 'settings',
        name: 'PortalSettings',
        component: () => import('@/views/portal/settings.vue'),
        meta: { title: '账号设置', hidden: true }
      },
      {
        path: 'messages',
        name: 'PortalMessages',
        component: () => import('@/views/portal/messages.vue'),
        meta: { title: '站内信', hidden: true }
      },
      {
        path: 'apply',
        name: 'PortalSubsystemApply',
        component: () => import('@/views/portal/apply.vue'),
        meta: { title: '子系统权限申请', hidden: true }
      },
      {
        path: 'applications',
        name: 'PortalApplications',
        component: () => import('@/views/portal/applications.vue'),
        meta: { title: '我的申请记录', hidden: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes: staticRoutes
})

// ==================== 路由守卫 — 管理后台与用户门户完全分离 ====================
router.beforeEach(async (to, _from, next) => {
  NProgress.start()
  ;(router as any)._navStart = Date.now()

  const title = to.meta.title as string
  document.title = title ? `${title} - 统一授权中台` : '统一授权中台'

  const token = getAccessToken()

  // 公开路由：无需登录即可访问
  const publicPaths = ['/adminLogin', '/login', '/login/callback', '/register']
  if (publicPaths.includes(to.path)) {
    next()
    return
  }

  // 未登录 → 根据路径前缀跳转对应登录页
  if (!token) {
    next(to.path.startsWith('/portal') ? '/login' : '/adminLogin')
    return
  }

  // 已登录但未获取用户信息
  const userStore = useUserStore()
  if (!userStore.userInfo) {
    try {
      await userStore.fetchUserInfo()
    } catch {
      userStore.logout()
      next(to.path.startsWith('/portal') ? '/login' : '/adminLogin')
      return
    }
  }

  // 菜单未加载（如页面刷新后从 localStorage 恢复了 userInfo，但菜单已丢失）
  if (!userStore.menus || userStore.menus.length === 0) {
    await userStore.fetchMenus()
  }

  // 管理后台路由：仅管理员可访问
  const isPortalRoute = to.path.startsWith('/portal')
  if (!isPortalRoute && !publicPaths.includes(to.path)) {
    if (!userStore.isAdmin) {
      // 非管理员无法访问后台，引导至管理后台登录页
      console.warn('[RouterGuard] 非管理员访问后台被拦截:', {
        path: to.path,
        roleCodes: userStore.userInfo?.roleCodes,
        userType: userStore.userInfo?.userType,
      })
      userStore.logout()
      next('/adminLogin')
      return
    }

    // 检查路由权限
    const requiredPerm = to.meta.permission as string
    if (requiredPerm && !userStore.hasPermission(requiredPerm)) {
      next('/dashboard')
      return
    }
  }

  next()
})

router.afterEach((to, from) => {
  NProgress.done()
  const start = (router as any)._navStart
  const duration = typeof start === 'number' ? Date.now() - start : undefined
  logRoute(from, to, duration)
})

export default router
