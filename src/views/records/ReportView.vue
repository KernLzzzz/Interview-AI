<template>
  <div class="page-container">
    <!-- 不存在 -->
    <el-empty v-if="!record" description="面试报告不存在">
      <el-button type="primary" @click="router.push('/records')">返回列表</el-button>
    </el-empty>

    <template v-else>
      <!-- 操作栏 -->
      <div class="action-bar">
        <el-button @click="router.push('/records')">
          <el-icon><ArrowLeft /></el-icon> 返回列表
        </el-button>
        <div class="right-actions">
          <el-tooltip content="在打印对话框中选择「另存为 PDF」即可导出 PDF" placement="top">
            <el-button type="primary" @click="printReport">
              <el-icon><Printer /></el-icon> 打印 / 导出PDF
            </el-button>
          </el-tooltip>
          <el-button type="success" @click="exportReport">
            <el-icon><Download /></el-icon> 导出JSON
          </el-button>
        </div>
      </div>

      <!-- 报告主体 -->
      <div class="report-body" id="report-print">
        <!-- 报告标题 -->
        <div class="report-header">
          <div class="report-title">
            <el-icon size="32" color="#409EFF"><Document /></el-icon>
            <div>
              <h1>AI 面试评估报告</h1>
              <p>AI Interview Assessment Report</p>
            </div>
          </div>
          <div class="report-meta">
            <span>报告编号：RPT-{{ String(record.id).padStart(6, '0') }}</span>
            <span>生成时间：{{ formatDateTime(record.completedAt || record.createdAt) }}</span>
          </div>
        </div>

        <!-- 总分 Banner -->
        <div class="score-banner" :class="evaluating ? 'is-evaluating' : ''">
          <template v-if="evaluating">
            <el-icon class="is-loading" size="40"><Loading /></el-icon>
            <div>
              <div class="banner-label">AI 评测中</div>
              <div class="banner-sub">报告生成需要几秒钟，请稍候...</div>
            </div>
          </template>
          <template v-else>
            <div class="score-left">
              <div class="score-level">{{ scoreLevel }}</div>
              <div class="banner-sub">综合评分</div>
            </div>
            <div class="score-right">
              <div class="big-score">{{ record.score }}</div>
              <div class="banner-sub">满分 100</div>
            </div>
          </template>
        </div>

        <!-- 基本信息 -->
        <el-card class="section-card">
          <template #header><span class="section-title">📋 基本信息</span></template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="面试场景">{{ record.scenarioName || `场景${record.scenarioId}` }}</el-descriptions-item>
            <el-descriptions-item label="面试状态">
              <el-tag :type="statusTypeMap[record.status]">{{ record.status }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDateTime(record.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="完成时间">{{ record.completedAt ? formatDateTime(record.completedAt) : '-' }}</el-descriptions-item>
            <el-descriptions-item label="作答时长">{{ record.duration ? `${record.duration} 分钟` : '-' }}</el-descriptions-item>
            <el-descriptions-item label="作答题数">{{ qaList.length }} 题</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- AI 评测反馈 -->
        <el-card class="section-card">
          <template #header><span class="section-title">🤖 AI 评测反馈</span></template>
          <div class="ai-feedback">
            <el-icon size="20" color="#909399"><ChatDotRound /></el-icon>
            <p>{{ parsedFeedback ? parsedFeedback.summary : (record.aiFeedback || (evaluating ? '评测生成中...' : '（暂无反馈）')) }}</p>
          </div>
        </el-card>

        <!-- 面试官评语（人工评分后出现） -->
        <el-card v-if="parsedFeedback?.interviewerComment" class="section-card">
          <template #header><span class="section-title">👨‍💼 面试官评语</span></template>
          <div class="ai-feedback">
            <el-icon size="20" color="#409EFF"><UserFilled /></el-icon>
            <p>{{ parsedFeedback.interviewerComment }}</p>
          </div>
        </el-card>

        <!-- 题目与作答 -->
        <el-card class="section-card">
          <template #header><span class="section-title">📝 题目与作答</span></template>
          <el-empty v-if="qaList.length === 0" description="暂无作答数据" :image-size="60" />
          <div v-else class="qa-list">
            <div v-for="(item, idx) in qaList" :key="item.id" class="qa-item">
              <div class="qa-question">
                <span class="qa-no">Q{{ idx + 1 }}</span>
                <span>{{ item.content }}</span>
              </div>
              <div class="qa-answer">
                <span class="qa-label">我的回答</span>
                <p>{{ item.answer || '（未作答）' }}</p>
              </div>
              <div v-if="item.feedback" class="qa-feedback">
                <span class="qa-fb-label">AI 点评</span>
                <el-tag size="small" :type="scoreTag(item.feedback.score)">
                  {{ item.feedback.score }}/10
                </el-tag>
                <p>{{ item.feedback.feedback }}</p>
              </div>
            </div>
          </div>
        </el-card>

        <!-- 报告底部 -->
        <div class="report-footer">
          <el-divider>— 报告结束 —</el-divider>
          <p class="footer-note">本报告由多模态智能模拟面试评测平台自动生成</p>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useRecordStore } from '@/stores/record'
import { formatDateTime, exportJson } from '@/utils/helpers'

const route = useRoute()
const router = useRouter()
const recordStore = useRecordStore()

const recordId = Number(route.params.id)
const record = computed(() => recordStore.getById(recordId))

const statusTypeMap: Record<string, string> = {
  '待开始': 'info', '进行中': 'warning', '已完成': 'success', '已取消': 'danger'
}

// AI 评测中：已完成但分数还没写回
const evaluating = computed(() =>
  record.value?.status === '已完成' && record.value.score == null
)

const scoreLevel = computed(() => {
  const s = record.value?.score ?? 0
  if (s >= 85) return '优秀'
  if (s >= 70) return '良好'
  if (s >= 60) return '及格'
  return '待提升'
})

// 解析逐题点评（新记录 aiFeedback 是 JSON；旧记录是纯文本 → 回退 null）
interface FeedbackItem { questionId: number; score: number; feedback: string }
interface ParsedFeedback { summary: string; items: FeedbackItem[]; interviewerComment?: string }

const parsedFeedback = computed<ParsedFeedback | null>(() => {
  const r = record.value
  if (!r?.aiFeedback) return null
  try {
    const p = JSON.parse(r.aiFeedback)
    if (p && typeof p === 'object' && Array.isArray(p.items)) {
      return p as ParsedFeedback
    }
    return null
  } catch {
    return null
  }
})

// 解析 answerData 展示题目与作答，并把逐题点评合并进每题
const qaList = computed(() => {
  const r = record.value
  if (!r?.answerData) return []
  let raw: Array<{ id: number; content: string; answer: string }> = []
  try {
    raw = JSON.parse(r.answerData)
  } catch {
    raw = []
  }
  const items = parsedFeedback.value?.items ?? []
  return raw.map(item => ({
    ...item,
    feedback: items.find(i => i.questionId === item.id)
  }))
})

function scoreTag(score: number) {
  if (score >= 8) return 'success'
  if (score >= 5) return 'warning'
  return 'danger'
}

// 轮询：AI 评测异步写分，报告页定时重拉
let pollTimer: ReturnType<typeof setInterval> | null = null

function startPolling() {
  let ticks = 0
  pollTimer = setInterval(async () => {
    ticks++
    await recordStore.fetchRecords()
    if (!evaluating.value || ticks >= 5) {
      if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
    }
  }, 2500)
}

onMounted(async () => {
  await recordStore.fetchRecords()
  if (evaluating.value) startPolling()
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})

function printReport() {
  window.print()
}

function exportReport() {
  if (!record.value) return
  exportJson(record.value, `面试报告_RPT${record.value.id}.json`)
  ElMessage.success('报告已导出')
}
</script>

<style scoped lang="scss">
.page-container {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
}

.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  .right-actions { display: flex; gap: 8px; }
}

.report-body {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
}

.report-header {
  padding: 28px 32px 20px;
  border-bottom: 1px solid #EBEEF5;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;

  .report-title {
    display: flex;
    align-items: center;
    gap: 14px;
    h1 { font-size: 22px; font-weight: 700; color: #303133; margin: 0 0 4px; }
    p { font-size: 12px; color: #909399; margin: 0; letter-spacing: 1px; }
  }

  .report-meta {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 4px;
    font-size: 12px;
    color: #909399;
  }
}

.score-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 28px 40px;
  color: #fff;
  background: linear-gradient(135deg, #1890ff, #096dd9);

  &.is-evaluating {
    justify-content: center;
    gap: 12px;
    background: linear-gradient(135deg, #909399, #606266);
  }

  .banner-label { font-size: 24px; font-weight: 700; }
  .banner-sub { font-size: 13px; opacity: 0.85; margin-top: 4px; }

  .score-left { text-align: left; }
  .score-right { text-align: right; }
  .score-level { font-size: 28px; font-weight: 700; }
  .big-score { font-size: 56px; font-weight: 700; line-height: 1; }
}

.section-card {
  border-radius: 0;
  border: none;
  border-bottom: 1px solid #EBEEF5;
  box-shadow: none !important;
  :deep(.el-card__header) {
    padding: 14px 28px;
    background: #fafafa;
  }
  :deep(.el-card__body) { padding: 20px 28px; }

  .section-title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }
}

.ai-feedback {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  background: #f8f9fa;
  border-radius: 8px;
  padding: 16px;
  p {
    margin: 0;
    line-height: 1.8;
    color: #303133;
    font-size: 14px;
    white-space: pre-wrap;
  }
}

.qa-list {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .qa-item {
    border: 1px solid #EBEEF5;
    border-radius: 8px;
    overflow: hidden;
  }
  .qa-question {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    padding: 12px 16px;
    background: #f8f9fa;
    font-size: 14px;
    color: #303133;
    line-height: 1.6;
    .qa-no { font-weight: 700; color: #409EFF; }
  }
  .qa-answer {
    padding: 12px 16px;
    .qa-label { font-size: 12px; color: #909399; display: block; margin-bottom: 6px; }
    p { margin: 0; font-size: 14px; color: #606266; line-height: 1.7; white-space: pre-wrap; }
  }
  .qa-feedback {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    padding: 10px 16px;
    background: #f0f7ff;
    border-top: 1px dashed #d9ecff;
    .qa-fb-label { font-size: 12px; color: #409EFF; font-weight: 600; white-space: nowrap; }
    p { margin: 0; font-size: 13px; color: #303133; line-height: 1.7; flex: 1; }
  }
}

.report-footer {
  padding: 12px 28px 20px;
  .footer-note {
    text-align: center;
    font-size: 12px;
    color: #c0c4cc;
  }
}

/* 打印样式 */
@media print {
  .action-bar { display: none !important; }
  .page-container { max-width: 100%; padding: 0; }
  .report-body { box-shadow: none; }
}
</style>
