<template>
  <!-- 未登录：只渲染路由视图（登录/注册页本身有完整布局） -->
  <router-view v-if="!authStore.isLoggedIn" />

  <!-- 面试过程使用全屏面试厅，不显示后台导航框架 -->
  <router-view v-else-if="route.meta.immersive" />

  <!-- 已登录：完整布局 -->
  <el-container v-else class="app-layout">
    <!-- 侧边栏 -->
    <el-aside width="232px" class="app-aside">
      <div class="logo">
        <div class="logo-mark">IA</div>
        <div class="logo-copy">
          <strong>INTERVIEW AI</strong>
          <span>模拟面试评测</span>
        </div>
      </div>

      <el-menu
        :default-active="activeMenu"
        router
        background-color="transparent"
        text-color="#9eafc3"
        active-text-color="#ffffff"
        class="side-menu"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>

      <!-- 底部用户信息 -->
      <div class="aside-footer">
        <el-avatar :size="32" style="background:#409EFF;flex-shrink:0;cursor:pointer" @click="router.push('/profile')">
          {{ authStore.username?.charAt(0) }}
        </el-avatar>
        <div class="user-info" style="cursor:pointer" @click="router.push('/profile')">
          <span class="user-name">{{ authStore.username }}</span>
          <el-tag size="small" :type="roleTagType">{{ authStore.userRole }}</el-tag>
        </div>
        <el-tooltip content="退出登录" placement="top">
          <el-button link style="color:#b0bec5" @click="handleLogout">
            <el-icon size="18"><SwitchButton /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </el-aside>

    <!-- 主内容区 -->
    <el-container>
      <!-- 顶部导航 -->
      <el-header class="app-header">
        <div class="header-left">
          <span class="header-eyebrow">WORKSPACE</span>
          <span class="page-title">{{ currentTitle }}</span>
        </div>
        <div class="header-right">
          <div class="system-state"><i></i><span>结构化评测工作台</span></div>
          <span class="time">{{ currentTime }}</span>
        </div>
      </el-header>

      <!-- 内容 -->
      <el-main class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// 侧边栏菜单：按角色展示
const menuItems = computed(() => {
  const role = authStore.userRole
  if (role === '候选人') {
    return [
      { path: '/home', icon: 'HomeFilled', label: '我的面试' },
      { path: '/scenarios', icon: 'Notebook', label: '场景广场' },
      { path: '/records', icon: 'VideoCamera', label: '面试记录' }
    ]
  }
  if (role === '面试官') {
    return [
      { path: '/dashboard', icon: 'DataAnalysis', label: '仪表盘' },
      { path: '/scenarios', icon: 'Notebook', label: '场景广场' },
      { path: '/records', icon: 'VideoCamera', label: '面试记录' }
    ]
  }
  return [
    { path: '/dashboard', icon: 'DataAnalysis', label: '仪表盘' },
    { path: '/scenarios', icon: 'Notebook', label: '场景广场' },
    { path: '/scenarios/manage', icon: 'FolderOpened', label: '场景管理' },
    { path: '/records', icon: 'VideoCamera', label: '面试记录' },
    { path: '/questions', icon: 'Document', label: '题库管理' },
    { path: '/users', icon: 'UserFilled', label: '用户管理' }
  ]
})

const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/home')) return '/home'
  if (path.startsWith('/dashboard')) return '/dashboard'
  if (path.startsWith('/scenarios')) return path.startsWith('/scenarios/manage') ? '/scenarios/manage' : '/scenarios'
  if (path.startsWith('/records')) return '/records'
  if (path.startsWith('/questions')) return '/questions'
  if (path.startsWith('/users')) return '/users'
  if (path.startsWith('/profile')) return ''
  return ''
})

const titleMap: Record<string, string> = {
  '/dashboard': '仪表盘',
  '/home': '我的面试',
  '/scenarios': '场景广场',
  '/scenarios/manage': '场景管理',
  '/records': '面试记录',
  '/config': '系统配置',
  '/questions': '题库管理',
  '/questions/create': '新建题目',
  '/users': '用户管理',
  '/profile': '个人中心'
}

const currentTitle = computed(() => {
  if (route.path.startsWith('/questions/edit/')) return '编辑题目'
  if (route.path.match(/\/records\/\d+\/interview/)) return '面试作答'
  if (route.path.match(/\/records\/\d+\/report/)) return '面试报告'
  return titleMap[route.path] || '面试平台管理系统'
})

const roleTagType = computed(() => {
  const map: Record<string, string> = { '管理员': 'danger', '面试官': 'primary', '候选人': 'success' }
  return map[authStore.userRole] ?? 'info'
})

async function handleLogout() {
  await ElMessageBox.confirm('确认退出登录？', '退出', { type: 'warning' })
  authStore.logout()
  router.push('/login')
}

const currentTime = ref('')
let timer: ReturnType<typeof setInterval>

function updateTime() {
  const now = new Date()
  currentTime.value = now.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  })
}

onMounted(() => {
  updateTime()
  timer = setInterval(updateTime, 1000)
})

onUnmounted(() => clearInterval(timer))
</script>

<style lang="scss">
:root {
  --ink-950: #102238;
  --ink-800: #233a52;
  --slate-600: #60758b;
  --line: #dbe4eb;
  --canvas: #edf2f5;
  --paper: #ffffff;
  --signal: #167487;
  --signal-soft: #dceff2;
}

* { box-sizing: border-box; margin: 0; padding: 0; }

body {
  font-family: Inter, "Noto Sans SC", "Microsoft YaHei", sans-serif;
  color: var(--ink-950);
  background: var(--canvas);
  text-rendering: optimizeLegibility;
}

.app-layout {
  height: 100vh;
  overflow: hidden;
}

.app-aside {
  width: 232px !important;
  background: var(--ink-950);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;

  .logo {
    display: flex;
    align-items: center;
    gap: 12px;
    min-height: 76px;
    padding: 15px 18px;
    color: #fff;
    border-bottom: 1px solid rgba(255,255,255,0.08);
  }

  .logo-mark {
    display: grid;
    place-items: center;
    width: 38px;
    height: 38px;
    flex: 0 0 38px;
    border: 1px solid rgba(255,255,255,.42);
    border-radius: 50%;
    font: 700 12px/1 ui-monospace, SFMono-Regular, Consolas, monospace;
    letter-spacing: .08em;
  }

  .logo-copy {
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 3px;
    strong { font-size: 13px; letter-spacing: .1em; }
    span { color: #89a0b7; font-size: 11px; letter-spacing: .08em; }
  }

  .side-menu {
    flex: 1;
    border-right: none;
    overflow-y: auto;

    .el-menu-item {
      height: 48px;
      line-height: 48px;
      margin: 4px 12px;
      border-radius: 4px;
      font-size: 14px;
      &.is-active {
        background: var(--signal) !important;
        color: #fff !important;
        box-shadow: inset 3px 0 0 #9bd4dc;
      }
      &:hover {
        background: rgba(255,255,255,0.08) !important;
      }
    }
  }

  .aside-footer {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px 16px;
    border-top: 1px solid rgba(255,255,255,0.08);
    background: rgba(0,0,0,0.15);

    .user-info {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 3px;
      .user-name {
        font-size: 13px;
        color: #e0e0e0;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    }
  }
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255,255,255,.94);
  border-bottom: 1px solid var(--line);
  padding: 0 28px;
  height: 76px;
  box-shadow: none;

  .header-left {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .header-eyebrow {
    font: 600 9px/1.2 ui-monospace, SFMono-Regular, Consolas, monospace;
    color: var(--signal);
    letter-spacing: .2em;
  }

  .page-title {
    font-size: 18px;
    font-weight: 650;
    color: var(--ink-950);
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 16px;
    .time {
      font: 12px/1.4 ui-monospace, SFMono-Regular, Consolas, monospace;
      color: var(--slate-600);
    }
  }

  .system-state {
    display: flex;
    align-items: center;
    gap: 7px;
    color: #456171;
    font-size: 12px;
    i {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: #2a9b7f;
      box-shadow: 0 0 0 4px rgba(42,155,127,.12);
    }
  }
}

.app-main {
  position: relative;
  background-color: var(--canvas);
  background-image: linear-gradient(rgba(35,58,82,.035) 1px, transparent 1px);
  background-size: 100% 28px;
  overflow-y: auto;
  padding: 0;
}

// 路由过渡动画
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

// Element Plus 全局调整
.el-card {
  border-color: var(--line) !important;
  border-radius: 5px;
  box-shadow: 0 8px 24px rgba(16,34,56,.045) !important;
}

.el-button--primary {
  --el-button-bg-color: var(--signal);
  --el-button-border-color: var(--signal);
  --el-button-hover-bg-color: #1d8799;
  --el-button-hover-border-color: #1d8799;
}

:focus-visible {
  outline: 3px solid rgba(22,116,135,.3);
  outline-offset: 2px;
}

@media (max-width: 760px) {
  .app-aside {
    width: 72px !important;
    .logo { justify-content: center; padding-inline: 10px; }
    .logo-copy, .user-info, .side-menu .el-menu-item span { display: none; }
    .side-menu .el-menu-item { justify-content: center; margin-inline: 9px; padding: 0 !important; }
    .aside-footer { justify-content: center; padding-inline: 8px; .el-button { display: none; } }
  }
  .app-header { height: 64px; padding-inline: 16px; }
  .app-header .time { display: none; }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation-duration: .01ms !important; transition-duration: .01ms !important; }
}
</style>
