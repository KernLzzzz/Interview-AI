<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="4" v-for="s in statCards" :key="s.label">
        <el-card class="stat-card" :style="{ borderTop: `3px solid ${s.color}` }">
          <div class="stat-value" :style="{ color: s.color }">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 筛选区域 -->
    <el-card class="filter-card">
      <el-row :gutter="12" align="middle">
        <el-col :span="8">
          <el-input
            v-model="filterForm.keyword"
            placeholder="搜索场景名称..."
            prefix-icon="Search"
            clearable
            @input="handleFilter"
          />
        </el-col>
        <el-col :span="5">
          <el-select
            v-model="filterForm.status"
            placeholder="面试状态"
            multiple
            collapse-tags
            clearable
            @change="handleFilter"
          >
            <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </el-col>
        <el-col :span="11" class="toolbar-right">
          <el-button @click="resetFilter">重置筛选</el-button>
          <el-button type="primary" @click="router.push('/scenarios')">+ 开始新面试</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card">
      <el-table
        v-loading="recordStore.loading"
        :data="recordStore.pagedRecords"
        border
        stripe
        row-key="id"
        @sort-change="handleSortChange"
      >
        <el-table-column type="index" label="序号" width="60" align="center" />

        <el-table-column prop="scenarioName" label="面试场景" min-width="180" show-overflow-tooltip />

        <el-table-column prop="interviewMode" label="方式" width="105" align="center">
          <template #default="{ row }">
            <span class="mode-cell"><el-icon><component :is="modeInfo(row.interviewMode).icon" /></el-icon>{{ modeInfo(row.interviewMode).label }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.status]" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="score" label="总分" width="90" sortable="custom" align="center">
          <template #default="{ row }">
            <span v-if="row.status === '已完成' && row.score != null"
              :style="{ color: getScoreColor(row.score), fontWeight: 700 }">
              {{ row.score }}
            </span>
            <span v-else style="color:#c0c4cc">-</span>
          </template>
        </el-table-column>

        <el-table-column prop="createdAt" label="创建时间" width="160" sortable="custom">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>

        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === '待开始'"
              link
              type="primary"
              size="small"
              @click="router.push(`/records/${row.id}/interview`)"
            >
              开始面试
            </el-button>
            <el-button
              v-if="row.status === '进行中'"
              link
              type="warning"
              size="small"
              @click="router.push(`/records/${row.id}/interview`)"
            >
              继续作答
            </el-button>
            <el-button
              v-if="row.status === '已完成'"
              link
              type="success"
              size="small"
              @click="router.push(`/records/${row.id}/report`)"
            >
              查看报告
            </el-button>
            <el-button
              v-if="row.status === '已完成' && canScore"
              link
              type="warning"
              size="small"
              @click="openScore(row)"
            >
              <el-icon><EditPen /></el-icon> 评分
            </el-button>
            <el-button
              v-if="row.status === '待开始'"
              link
              type="danger"
              size="small"
              @click="handleCancel(row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="recordStore.pagination.currentPage"
          v-model:page-size="recordStore.pagination.pageSize"
          :total="recordStore.pagination.total"
          :page-sizes="recordStore.pagination.pageSizes"
          layout="total, sizes, prev, pager, next, jumper"
          background
        />
      </div>
    </el-card>

    <!-- 面试官评分弹窗 -->
    <el-dialog v-model="scoreDialogVisible" title="面试评分" width="480px">
      <el-form label-width="80px">
        <el-form-item label="分数">
          <el-input-number v-model="scoreForm.score" :min="0" :max="100" :step="1" />
          <span class="score-hint">0-100，将覆盖 AI 评分</span>
        </el-form-item>
        <el-form-item label="评语">
          <el-input
            v-model="scoreForm.comment"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="面试官评语（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scoreDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submittingScore" @click="submitScore">保存评分</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRecordStore } from '@/stores/record'
import { useAuthStore } from '@/stores/auth'
import { scoreRecordApi } from '@/api/record'
import { formatDateTime } from '@/utils/helpers'

const router = useRouter()
const recordStore = useRecordStore()
const authStore = useAuthStore()

// 评分权限：候选人无 record:score
const canScore = computed(() => authStore.userRole !== '候选人')

const statusOptions = ['待开始', '进行中', '已完成', '已取消']
const modeMap = {
  text: { label: '文本', icon: 'EditPen' },
  voice: { label: '语音', icon: 'Microphone' },
  video: { label: '视频', icon: 'VideoCamera' }
} as const
function modeInfo(mode: unknown) {
  return mode === 'voice' || mode === 'video' || mode === 'text' ? modeMap[mode] : modeMap.text
}

const statusTypeMap: Record<string, string> = {
  '待开始': 'info',
  '进行中': 'warning',
  '已完成': 'success',
  '已取消': 'danger'
}

function getScoreColor(score: number) {
  if (score >= 85) return '#67C23A'
  if (score >= 70) return '#409EFF'
  if (score >= 60) return '#E6A23C'
  return '#F56C6C'
}

// 统计卡片
const stats = computed(() => recordStore.statistics)
const statCards = computed(() => [
  { label: '全部记录', value: stats.value.total,        color: '#909399' },
  { label: '待开始',   value: stats.value.pending,      color: '#909399' },
  { label: '进行中',   value: stats.value.inProgress,   color: '#E6A23C' },
  { label: '已完成',   value: stats.value.completed,    color: '#67C23A' },
  { label: '已取消',   value: stats.value.cancelled,    color: '#F56C6C' },
  { label: '平均分',   value: stats.value.avgScore,     color: '#409EFF' }
])

// 筛选
const filterForm = reactive({
  keyword: '',
  status: [] as string[]
})

function handleFilter() {
  recordStore.setFilter({
    keyword: filterForm.keyword,
    status: filterForm.status
  })
}

function resetFilter() {
  filterForm.keyword = ''
  filterForm.status = []
  recordStore.setFilter({})
}

function handleSortChange({ prop, order }: { prop: string; order: any }) {
  recordStore.setSort(prop, order)
}

async function handleCancel(row: any) {
  const confirmed = await ElMessageBox.confirm(
    `确认取消「${row.scenarioName}」这场面试？`,
    '取消确认',
    { type: 'warning' }
  ).catch(() => false)
  if (!confirmed) return
  await recordStore.cancelRecord(row.id)
  ElMessage.success('面试已取消')
}

// ===================== 面试官评分 =====================
const scoreDialogVisible = ref(false)
const scoringRecord = ref<any>(null)
const submittingScore = ref(false)
const scoreForm = reactive({ score: 70, comment: '' })

function openScore(row: any) {
  scoringRecord.value = row
  scoreForm.score = row.score ?? 70
  scoreForm.comment = ''
  scoreDialogVisible.value = true
}

async function submitScore() {
  if (!scoringRecord.value) return
  submittingScore.value = true
  try {
    await scoreRecordApi(scoringRecord.value.id, { score: scoreForm.score, comment: scoreForm.comment })
    ElMessage.success('评分已保存')
    scoreDialogVisible.value = false
    await recordStore.fetchRecords()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '评分失败')
  } finally {
    submittingScore.value = false
  }
}

onMounted(async () => {
  await recordStore.fetchRecords()
})
</script>

<style scoped lang="scss">
.page-container {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stat-row {
  .stat-card {
    text-align: center;
    padding: 4px;
    :deep(.el-card__body) { padding: 16px 12px; }
    .stat-value {
      font-size: 28px;
      font-weight: 700;
      line-height: 1.2;
    }
    .stat-label {
      font-size: 12px;
      color: #909399;
      margin-top: 4px;
    }
  }
}

.filter-card :deep(.el-card__body) { padding: 14px 16px; }
.toolbar-right { text-align: right; }

.table-card {
  :deep(.el-card__body) { padding: 0; }
}
.mode-cell { display: inline-flex; align-items: center; gap: 6px; color: #526c7b; }

.pagination-wrap {
  padding: 14px 16px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid #EBEEF5;
}
</style>
