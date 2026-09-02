import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { InterviewRecord, RecordFilter, Pagination } from '@/types'
import {
  listRecordsApi,
  createRecordApi,
  startRecordApi,
  submitRecordApi,
  cancelRecordApi,
  voToRecord
} from '@/api/record'
import { listScenariosApi, type Scenario } from '@/api/scenario'

export const useRecordStore = defineStore('record', () => {
  const allRecords = ref<InterviewRecord[]>([])
  const scenarios = ref<Scenario[]>([])
  const filter = ref<RecordFilter>({})
  const sortField = ref('')
  const sortOrder = ref<'ascending' | 'descending' | null>(null)
  const pagination = ref<Pagination>({
    currentPage: 1,
    pageSize: 10,
    total: 0,
    pageSizes: [10, 20, 50]
  })
  const loading = ref(false)

  // 场景 id → 名称（无参时用当前场景列表）
  function scenarioNameOf(scenarioId: number, sc?: Scenario[]): string {
    return (sc ?? scenarios.value).find(s => s.id === scenarioId)?.name ?? `场景${scenarioId}`
  }

  // 拉取记录 + 场景（记录带 scenarioId，用场景名展示）
  async function fetchRecords() {
    loading.value = true
    try {
      const [records, sc] = await Promise.all([listRecordsApi(), listScenariosApi()])
      scenarios.value = sc
      allRecords.value = records.map(r => voToRecord(r, scenarioNameOf(r.scenarioId, sc)))
    } finally {
      loading.value = false
    }
  }

  // 创建面试（返回新记录，用于跳转作答页）
  async function createRecord(scenarioId: number): Promise<InterviewRecord> {
    const vo = await createRecordApi(scenarioId)
    await fetchRecords()
    return voToRecord(vo, scenarioNameOf(vo.scenarioId))
  }

  // 开始面试（后端抽题写 questionData）
  async function startRecord(id: number): Promise<InterviewRecord> {
    const vo = await startRecordApi(id)
    await fetchRecords()
    return voToRecord(vo, scenarioNameOf(vo.scenarioId))
  }

  // 提交答案（触发异步 AI 评测）
  async function submitRecord(id: number, answerData: string) {
    await submitRecordApi(id, answerData)
    await fetchRecords()
  }

  // 取消面试
  async function cancelRecord(id: number) {
    await cancelRecordApi(id)
    await fetchRecords()
  }

  // 根据ID获取记录
  function getById(id: number): InterviewRecord | undefined {
    return allRecords.value.find(r => r.id === id)
  }

  // 筛选后的数据
  const filteredRecords = computed(() => {
    let list = [...allRecords.value]

    if (filter.value.keyword) {
      const kw = filter.value.keyword.toLowerCase()
      list = list.filter(r => (r.scenarioName ?? '').toLowerCase().includes(kw))
    }
    if (filter.value.status && filter.value.status.length > 0) {
      list = list.filter(r => filter.value.status!.includes(r.status))
    }

    // 排序
    if (sortField.value && sortOrder.value) {
      list.sort((a: any, b: any) => {
        const av = a[sortField.value]
        const bv = b[sortField.value]
        if (sortOrder.value === 'ascending') return av > bv ? 1 : -1
        return av < bv ? 1 : -1
      })
    }

    return list
  })

  const pagedRecords = computed(() => {
    pagination.value.total = filteredRecords.value.length
    const start = (pagination.value.currentPage - 1) * pagination.value.pageSize
    return filteredRecords.value.slice(start, start + pagination.value.pageSize)
  })

  // 统计（列表页顶部卡片）
  const statistics = computed(() => {
    const scored = allRecords.value.filter(r => r.status === '已完成' && r.score != null)
    const avg = scored.length
      ? Math.round(scored.reduce((s, r) => s + (r.score ?? 0), 0) / scored.length)
      : 0
    return {
      total: allRecords.value.length,
      completed: allRecords.value.filter(r => r.status === '已完成').length,
      pending: allRecords.value.filter(r => r.status === '待开始').length,
      inProgress: allRecords.value.filter(r => r.status === '进行中').length,
      cancelled: allRecords.value.filter(r => r.status === '已取消').length,
      avgScore: avg
    }
  })

  // ===================== 筛选/排序 =====================
  function setFilter(f: RecordFilter) {
    filter.value = { ...f }
    pagination.value.currentPage = 1
  }

  function setSort(field: string, order: 'ascending' | 'descending' | null) {
    sortField.value = field
    sortOrder.value = order
  }

  return {
    allRecords,
    scenarios,
    filter,
    pagination,
    loading,
    filteredRecords,
    pagedRecords,
    statistics,
    fetchRecords,
    createRecord,
    startRecord,
    submitRecord,
    cancelRecord,
    getById,
    setFilter,
    setSort
  }
})
