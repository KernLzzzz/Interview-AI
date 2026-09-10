<template>
  <div class="page-container">
    <div class="page-header">
      <el-button text @click="router.push('/records')">
        <el-icon><ArrowLeft /></el-icon> 返回记录
      </el-button>
      <div class="title-wrap">
        <h2>面试作答</h2>
        <el-tag v-if="record" :type="statusTypeMap[record.status]" size="small">{{ record.status }}</el-tag>
      </div>
    </div>

    <div v-loading="loading">
      <!-- 待开始：点击开始抽题 -->
      <el-empty v-if="record && record.status === '待开始'" description="准备好后点击开始，系统将为你抽取题目">
        <el-button type="primary" :loading="starting" @click="handleStart">开始面试</el-button>
        <el-button @click="handleCancel">取消</el-button>
      </el-empty>

      <!-- 进行中：逐题作答 -->
      <template v-else-if="record && record.status === '进行中' && questions.length">
        <!-- 顶部：进度 + 倒计时 -->
        <div class="interview-bar">
          <div class="progress-info">
            <span>第 {{ currentIndex + 1 }} / {{ questions.length }} 题</span>
            <el-tag size="small" :type="answeredCount === questions.length ? 'success' : 'info'" effect="plain">
              已答 {{ answeredCount }} 题
            </el-tag>
          </div>
          <div class="timer" :class="{ 'timer-warn': remainingSeconds < 180 }">
            <el-icon><Timer /></el-icon>
            <span>{{ formatTime(remainingSeconds) }}</span>
          </div>
        </div>

        <!-- 当前题 -->
        <el-card class="question-card" shadow="hover">
          <template #header>
            <div class="q-header">
              <span class="q-no">第 {{ currentIndex + 1 }} 题</span>
              <el-tag size="small" :type="difficultyTag(currentQuestion.difficulty)">
                {{ difficultyLabel(currentQuestion.difficulty) }}
              </el-tag>
            </div>
          </template>
          <p class="q-content">{{ currentQuestion.content }}</p>
          <el-input
            v-model="answers[currentQuestion.id]"
            type="textarea"
            :rows="8"
            placeholder="请输入你的回答..."
            maxlength="2000"
            show-word-limit
          />
        </el-card>

        <!-- 题号点阵 -->
        <div class="question-dots">
          <div
            v-for="(q, idx) in questions"
            :key="q.id"
            class="dot"
            :class="{ active: idx === currentIndex, answered: isAnswered(q.id) }"
            @click="currentIndex = idx"
          >{{ idx + 1 }}</div>
        </div>

        <!-- 底部导航 -->
        <div class="nav-bar">
          <el-button :disabled="currentIndex === 0" @click="currentIndex--">
            <el-icon><ArrowLeft /></el-icon> 上一题
          </el-button>
          <el-button
            :disabled="currentIndex >= questions.length - 1"
            type="primary"
            @click="currentIndex++"
          >
            下一题 <el-icon><ArrowRight /></el-icon>
          </el-button>
          <div class="nav-spacer"></div>
          <el-button type="success" :loading="submitting" @click="handleSubmit()">
            提交面试
          </el-button>
        </div>
      </template>

      <el-empty v-else-if="record && record.status === '已完成'" description="面试已提交">
        <el-button type="primary" @click="router.push(`/records/${record.id}/report`)">查看报告</el-button>
      </el-empty>

      <el-empty v-else description="加载中或记录不存在" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRecordStore } from '@/stores/record'

const INTERVIEW_LIMIT_SECONDS = 900   // 15 分钟，从 startedAt 起算

const route = useRoute()
const router = useRouter()
const recordStore = useRecordStore()

const id = Number(route.params.id)
const record = computed(() => recordStore.getById(id))
const questions = computed(() => record.value?.questionData ?? [])
const answers = ref<Record<number, string>>({})

const loading = ref(false)
const starting = ref(false)
const submitting = ref(false)
const finished = ref(false)   // 已提交/已取消，避免卸载时重写草稿

// 逐题导航
const currentIndex = ref(0)
const currentQuestion = computed(() => questions.value[currentIndex.value] ?? questions.value[0])
const answeredCount = computed(() =>
  questions.value.filter(q => (answers.value[q.id] ?? '').trim()).length
)

function isAnswered(qid: number) {
  return (answers.value[qid] ?? '').trim().length > 0
}

const statusTypeMap: Record<string, string> = {
  '待开始': 'info', '进行中': 'warning', '已完成': 'success', '已取消': 'danger'
}

function difficultyLabel(d: number) {
  return d === 1 ? '简单' : d === 2 ? '中等' : '困难'
}
function difficultyTag(d: number) {
  return d === 1 ? 'success' : d === 2 ? 'warning' : 'danger'
}

// ===================== 倒计时 =====================
const remainingSeconds = ref(INTERVIEW_LIMIT_SECONDS)
let timerHandle: ReturnType<typeof setInterval> | null = null

function formatTime(sec: number) {
  const m = Math.floor(Math.max(0, sec) / 60)
  const s = Math.max(0, sec) % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function startTimer() {
  stopTimer()
  const started = record.value?.startedAt ? new Date(record.value.startedAt).getTime() : Date.now()
  const elapsed = Math.floor((Date.now() - started) / 1000)
  remainingSeconds.value = Math.max(0, INTERVIEW_LIMIT_SECONDS - elapsed)
  timerHandle = setInterval(() => {
    remainingSeconds.value--
    if (remainingSeconds.value <= 0) {
      stopTimer()
      ElMessage.warning('时间到，正在自动提交…')
      handleSubmit(true)
    }
  }, 1000)
}

function stopTimer() {
  if (timerHandle) { clearInterval(timerHandle); timerHandle = null }
}

// ===================== 草稿自动保存（localStorage） =====================
function draftKey() { return `interview_draft_${id}` }
function loadDraft() {
  try {
    const raw = localStorage.getItem(draftKey())
    if (raw) {
      const parsed = JSON.parse(raw)
      if (parsed && typeof parsed === 'object' && parsed.answers) {
        Object.assign(answers.value, parsed.answers)
      }
    }
  } catch { /* 草稿损坏忽略 */ }
}
function saveDraft() {
  localStorage.setItem(draftKey(), JSON.stringify({ answers: answers.value, updatedAt: new Date().toISOString() }))
}
function clearDraft() {
  localStorage.removeItem(draftKey())
}

let saveHandle: ReturnType<typeof setTimeout> | null = null
watch(answers, () => {
  if (saveHandle) clearTimeout(saveHandle)
  saveHandle = setTimeout(saveDraft, 500)
}, { deep: true })

onBeforeUnmount(() => {
  stopTimer()
  if (saveHandle) clearTimeout(saveHandle)
  if (!finished.value) saveDraft()   // 未提交则保留草稿
})

// ===================== 操作 =====================
onMounted(async () => {
  loading.value = true
  try {
    await recordStore.fetchRecords()
    if (!record.value) {
      ElMessage.error('记录不存在')
      router.push('/records')
    } else if (record.value.status === '进行中') {
      loadDraft()
      startTimer()
    }
  } finally {
    loading.value = false
  }
})

async function handleStart() {
  starting.value = true
  try {
    await recordStore.startRecord(id)
    loadDraft()
    startTimer()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '开始面试失败')
  } finally {
    starting.value = false
  }
}

async function handleSubmit(auto = false) {
  if (submitting.value) return
  if (answeredCount.value === 0) {
    ElMessage.warning('请至少回答一道题')
    return
  }
  if (!auto) {
    const confirmed = await ElMessageBox.confirm(
      `已作答 ${answeredCount.value}/${questions.value.length} 题，确认提交？提交后将触发 AI 评测。`,
      '提交确认',
      { type: 'warning' }
    ).catch(() => false)
    if (!confirmed) return
  }

  submitting.value = true
  try {
    const submission = questions.value.map(q => ({
      questionId: q.id,
      answer: answers.value[q.id] ?? ''
    }))
    await recordStore.submitRecord(id, submission)
    clearDraft()
    finished.value = true
    ElMessage.success('已提交，AI 评测中...')
    router.push(`/records/${id}/report`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '提交失败')
  } finally {
    submitting.value = false
  }
}

async function handleCancel() {
  const confirmed = await ElMessageBox.confirm('确认取消这场面试？', '取消确认', { type: 'warning' }).catch(() => false)
  if (!confirmed) return
  await recordStore.cancelRecord(id)
  clearDraft()
  finished.value = true
  ElMessage.success('面试已取消')
  router.push('/records')
}
</script>

<style scoped lang="scss">
.page-container {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 860px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  .title-wrap {
    display: flex;
    align-items: center;
    gap: 8px;
    h2 { margin: 0; color: #303133; }
  }
}

.interview-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #EBEEF5;

  .progress-info {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    color: #303133;
    font-weight: 600;
  }

  .timer {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 18px;
    font-weight: 700;
    color: #409EFF;
    font-variant-numeric: tabular-nums;

    &.timer-warn { color: #F56C6C; animation: pulse 1s infinite; }
  }
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.question-card {
  .q-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    .q-no { font-weight: 600; color: #303133; }
  }
  .q-content {
    font-size: 15px;
    color: #303133;
    line-height: 1.8;
    margin: 0 0 14px;
  }
}

.question-dots {
  display: flex;
  justify-content: center;
  gap: 10px;
  flex-wrap: wrap;

  .dot {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    cursor: pointer;
    background: #f5f7fa;
    color: #909399;
    border: 2px solid transparent;
    transition: all 0.15s;

    &.active {
      border-color: #409EFF;
      color: #409EFF;
      font-weight: 700;
    }
    &.answered {
      background: #409EFF;
      color: #fff;
    }
    &.answered.active {
      background: #fff;
    }
  }
}

.nav-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0 24px;

  .nav-spacer { flex: 1; }
}
</style>
