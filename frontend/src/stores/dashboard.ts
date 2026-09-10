import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { TodoItem } from '@/types'
import { getStatsApi, type StatsOverview } from '@/api/stats'
import { listScenariosApi, type Scenario } from '@/api/scenario'
import { getEvaluationMetricsApi, type EvaluationMetrics } from '@/api/record'
import todosData from '@/mock/todos.json'

function mockDelay(ms = 400) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

// 后端状态码 → 中文（与记录模块一致）
const STATUS_MAP: Record<string, string> = {
  pending: '待开始',
  ongoing: '进行中',
  completed: '已完成',
  cancelled: '已取消'
}

export const useDashboardStore = defineStore('dashboard', () => {
  // ===================== State =====================
  const todos = ref<TodoItem[]>(todosData as TodoItem[])
  const loading = ref(false)
  const statsLoading = ref(false)
  const stats = ref<StatsOverview>({
    total: 0, pending: 0, ongoing: 0, completed: 0, cancelled: 0,
    avgScore: null, passRate: null, completedToday: 0, totalCandidates: 0,
    trend: [], recent: []
  })
  const scenarios = ref<Scenario[]>([])
  const evaluationMetrics = ref<EvaluationMetrics>({ sampleSize: 0, success: 0, failed: 0, inFlight: 0, successRate: 0, averageLatencyMs: 0, p95LatencyMs: 0, retryRate: 0, remoteAiTasks: 0, localTasks: 0 })

  // ===================== 最近面试记录（真实，带场景名） =====================
  const recentRecords = computed(() =>
    stats.value.recent.map(r => ({
      id: r.id,
      scenarioId: r.scenarioId,
      scenarioName: scenarios.value.find(s => s.id === r.scenarioId)?.name ?? `场景${r.scenarioId}`,
      status: STATUS_MAP[r.status] ?? r.status,
      score: r.score,
      createdAt: r.createdAt
    }))
  )

  // ===================== 待办事项 computed（本地 mock，保留） =====================
  const pendingTodos = computed(() =>
    todos.value.filter(t => t.status !== '已完成')
      .sort((a, b) => {
        const priorityOrder: Record<string, number> = { '紧急': 0, '普通': 1, '低优先级': 2 }
        return priorityOrder[a.priority] - priorityOrder[b.priority]
      })
  )

  const completedTodos = computed(() =>
    todos.value.filter(t => t.status === '已完成')
  )

  const urgentTodoCount = computed(() =>
    todos.value.filter(t => t.priority === '紧急' && t.status !== '已完成').length
  )

  // ===================== 加载统计（真实接口） =====================
  async function fetchStats() {
    statsLoading.value = true
    try {
      const [s, sc, metrics] = await Promise.all([getStatsApi(), listScenariosApi(), getEvaluationMetricsApi()])
      stats.value = s
      scenarios.value = sc
      evaluationMetrics.value = metrics
    } finally {
      statsLoading.value = false
    }
  }

  // ===================== 待办方法（mock，保留） =====================
  async function fetchTodos() {
    loading.value = true
    await mockDelay(400)
    loading.value = false
  }

  async function updateTodoStatus(id: number, status: TodoItem['status']): Promise<boolean> {
    await mockDelay(300)
    const todo = todos.value.find(t => t.id === id)
    if (!todo) return false
    todo.status = status
    todo.updateTime = new Date().toISOString()
    return true
  }

  async function addTodo(data: Omit<TodoItem, 'id' | 'createTime' | 'updateTime'>): Promise<TodoItem> {
    loading.value = true
    await mockDelay(400)
    const now = new Date().toISOString()
    const newTodo: TodoItem = {
      ...data,
      id: Date.now(),
      createTime: now,
      updateTime: now
    }
    todos.value.unshift(newTodo)
    loading.value = false
    return newTodo
  }

  async function deleteTodo(id: number): Promise<boolean> {
    await mockDelay(300)
    const idx = todos.value.findIndex(t => t.id === id)
    if (idx === -1) return false
    todos.value.splice(idx, 1)
    return true
  }

  return {
    todos,
    loading,
    statsLoading,
    stats,
    evaluationMetrics,
    recentRecords,
    pendingTodos,
    completedTodos,
    urgentTodoCount,
    fetchStats,
    fetchTodos,
    updateTodoStatus,
    addTodo,
    deleteTodo
  }
})
