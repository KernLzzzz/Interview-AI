import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Question, QuestionFilter, Pagination } from '@/types'
import {
  listQuestionsApi,
  createQuestionApi,
  updateQuestionApi,
  deleteQuestionApi,
  voToQuestion,
  questionToPayload
} from '@/api/question'
import { listScenariosApi, type Scenario } from '@/api/scenario'

export const useQuestionStore = defineStore('question', () => {
  const allQuestions = ref<Question[]>([])
  const scenarios = ref<Scenario[]>([])
  const filter = ref<QuestionFilter>({})
  const sortField = ref<string>('')
  const sortOrder = ref<'ascending' | 'descending' | null>(null)
  const pagination = ref<Pagination>({
    currentPage: 1,
    pageSize: 10,
    total: 0,
    pageSizes: [10, 20, 50, 100]
  })

  // 从后端拉取题目 + 场景（全量，客户端筛选/分页/排序）
  async function fetchQuestions() {
    const [list, sc] = await Promise.all([listQuestionsApi(), listScenariosApi()])
    allQuestions.value = list.map(voToQuestion)
    scenarios.value = sc
  }

  // 场景 id → 名称
  function scenarioName(id: number): string {
    return scenarios.value.find(s => s.id === id)?.name ?? `场景${id}`
  }

  // 筛选后的数据
  const filteredQuestions = computed(() => {
    let list = [...allQuestions.value]

    if (filter.value.keyword) {
      const kw = filter.value.keyword.toLowerCase()
      list = list.filter(q => q.content.toLowerCase().includes(kw))
    }
    if (filter.value.type && filter.value.type.length > 0) {
      list = list.filter(q => filter.value.type!.includes(q.type))
    }
    if (filter.value.difficulty && filter.value.difficulty.length > 0) {
      list = list.filter(q => filter.value.difficulty!.includes(q.difficulty))
    }
    if (filter.value.status && filter.value.status.length > 0) {
      list = list.filter(q => filter.value.status!.includes(q.status))
    }

    // 排序
    if (sortField.value && sortOrder.value) {
      list.sort((a: any, b: any) => {
        const aVal = a[sortField.value]
        const bVal = b[sortField.value]
        if (sortOrder.value === 'ascending') return aVal > bVal ? 1 : -1
        return aVal < bVal ? 1 : -1
      })
    }

    return list
  })

  // 分页后的数据
  const pagedQuestions = computed(() => {
    const total = filteredQuestions.value.length
    pagination.value.total = total
    const start = (pagination.value.currentPage - 1) * pagination.value.pageSize
    const end = start + pagination.value.pageSize
    return filteredQuestions.value.slice(start, end)
  })

  // 根据ID获取题目
  function getById(id: number): Question | undefined {
    return allQuestions.value.find(q => q.id === id)
  }

  // 新增（id=0）或更新题目，保存后重新拉取
  async function saveQuestion(question: Question) {
    if (question.id) {
      await updateQuestionApi(question.id, questionToPayload(question))
    } else {
      await createQuestionApi(questionToPayload(question))
    }
    await fetchQuestions()
  }

  // 删除题目
  async function deleteQuestion(id: number) {
    await deleteQuestionApi(id)
    await fetchQuestions()
  }

  // 批量删除
  async function batchDelete(ids: number[]) {
    await Promise.all(ids.map(id => deleteQuestionApi(id)))
    await fetchQuestions()
  }

  // 设置筛选条件
  function setFilter(f: QuestionFilter) {
    filter.value = { ...f }
    pagination.value.currentPage = 1
  }

  // 设置排序
  function setSort(field: string, order: 'ascending' | 'descending' | null) {
    sortField.value = field
    sortOrder.value = order
  }

  return {
    allQuestions,
    scenarios,
    filter,
    sortField,
    sortOrder,
    pagination,
    filteredQuestions,
    pagedQuestions,
    scenarioName,
    fetchQuestions,
    getById,
    saveQuestion,
    deleteQuestion,
    batchDelete,
    setFilter,
    setSort
  }
})
