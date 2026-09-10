import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  // ========== 不需要登录 ==========
  {
    path: '/login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { title: '注册', public: true }
  },

  // ========== 需要登录 ==========
  {
    path: '/',
    redirect: () => useAuthStore().userRole === '候选人' ? '/home' : '/dashboard'
  },

  {
    path: '/home',
    component: () => import('@/views/home/CandidateHome.vue'),
    meta: { title: '我的面试', roles: ['候选人'] }
  },

  {
    path: '/dashboard',
    component: () => import('@/views/dashboard/DashboardView.vue'),
    meta: { title: '仪表盘', roles: ['管理员', '面试官'] }
  },

  {
    path: '/config',
    component: () => import('@/views/config/SystemConfig.vue'),
    meta: { title: '系统配置' }
  },
  {
    path: '/questions',
    component: () => import('@/views/questions/QuestionList.vue'),
    meta: { title: '题库管理', roles: ['管理员'] }
  },
  {
    path: '/questions/create',
    component: () => import('@/views/questions/QuestionForm.vue'),
    meta: { title: '新建题目', roles: ['管理员'] }
  },
  {
    path: '/questions/edit/:id',
    component: () => import('@/views/questions/QuestionForm.vue'),
    meta: { title: '编辑题目', roles: ['管理员'] }
  },
  {
    path: '/users',
    component: () => import('@/views/users/UserList.vue'),
    meta: { title: '用户管理', roles: ['管理员'] }
  },
  {
    path: '/profile',
    component: () => import('@/views/profile/ProfileView.vue'),
    meta: { title: '个人中心' }
  },

  // ========== 场景广场 ==========
  {
    path: '/scenarios',
    component: () => import('@/views/scenarios/ScenarioList.vue'),
    meta: { title: '场景广场' }
  },
  {
    path: '/scenarios/manage',
    component: () => import('@/views/scenarios/ScenarioManage.vue'),
    meta: { title: '场景管理', roles: ['管理员'] }
  },

  // ========== 面试记录 ==========
  {
    path: '/records',
    component: () => import('@/views/records/RecordList.vue'),
    meta: { title: '面试记录' }
  },
  {
    path: '/records/:id/interview',
    component: () => import('@/views/records/InterviewTake.vue'),
    meta: { title: '面试作答', immersive: true }
  },
  {
    path: '/records/:id/report',
    component: () => import('@/views/records/ReportView.vue'),
    meta: { title: '面试报告' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 各角色默认首页：候选人→/home，其余→/dashboard
function roleHome(): string {
  return useAuthStore().userRole === '候选人' ? '/home' : '/dashboard'
}

// 路由守卫：登录校验 + 角色权限
router.beforeEach((to, _from, next) => {
  // pinia 在 router.beforeEach 中需要通过函数调用方式获取 store
  const authStore = useAuthStore()

  if (to.meta.public) {
    // 已登录用户访问登录/注册页，直接跳到各自首页
    if (authStore.isLoggedIn) {
      return next(roleHome())
    }
    return next()
  }

  if (!authStore.isLoggedIn) {
    return next('/login')
  }

  // 角色路由守卫：路由声明了 roles 且不含当前角色 → 弹回各自首页
  const roles = to.meta.roles as string[] | undefined
  if (roles && !roles.includes(authStore.userRole)) {
    return next(roleHome())
  }

  next()
})

export default router
