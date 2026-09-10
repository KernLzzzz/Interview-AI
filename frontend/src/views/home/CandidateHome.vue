<template>
  <div class="candidate-home">
    <!-- 欢迎 + 主 CTA -->
    <div class="hero">
      <div class="hero-left">
        <div class="hero-avatar">{{ authStore.username?.charAt(0) }}</div>
        <div>
          <div class="hero-title">你好，{{ authStore.username }}！</div>
          <div class="hero-sub">通过 AI 模拟面试练习，拿到针对性反馈，快速提升</div>
        </div>
      </div>
      <div class="hero-right">
        <el-button type="primary" size="large" @click="$router.push('/scenarios')">
          <el-icon style="margin-right:4px"><Plus /></el-icon> 开始一场新的面试
        </el-button>
      </div>
    </div>

    <!-- 有进行中的面试 → 提示继续 -->
    <el-alert
      v-if="ongoingRecord"
      type="warning"
      :closable="false"
      show-icon
      class="continue-alert"
    >
      <template #title>
        有一场进行中的面试：{{ ongoingRecord.scenarioName }}
        <el-button link type="warning" @click="goToRecord(ongoingRecord)">继续作答 →</el-button>
      </template>
    </el-alert>

    <!-- 我的统计 -->
    <div class="stat-cards" v-loading="loading">
      <div v-for="card in statCards" :key="card.key" class="stat-card">
        <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
        <div class="stat-label">{{ card.label }}</div>
      </div>
    </div>

    <!-- 最近我的面试 -->
    <el-card class="recent-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-header-title"><el-icon color="#409EFF"><VideoCamera /></el-icon> 最近面试</span>
          <el-button link type="primary" @click="$router.push('/records')">查看全部</el-button>
        </div>
      </template>
      <div class="recent-list" v-loading="loading">
        <div v-for="r in recent" :key="r.id" class="recent-item" @click="goToRecord(r)">
          <div class="recent-info">
            <div class="recent-name">{{ r.scenarioName }}</div>
            <div class="recent-time">{{ formatRelativeTime(r.createdAt) }}</div>
          </div>
          <el-tag :type="statusTag(r.status)" size="small">{{ r.status }}</el-tag>
          <div v-if="r.score != null" class="recent-score">{{ r.score }} 分</div>
          <el-icon v-else color="#c0c4cc"><ArrowRight /></el-icon>
        </div>

        <el-empty v-if="!loading && recent.length === 0" description="还没有面试记录，去开始第一场吧" :image-size="70">
          <el-button type="primary" @click="$router.push('/scenarios')">开始面试</el-button>
        </el-empty>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useRecordStore } from '@/stores/record'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const recordStore = useRecordStore()
const authStore = useAuthStore()
const loading = ref(false)

// 我的统计（从自己的记录实时计算）
const stats = computed(() => {
  const records = recordStore.allRecords
  const scored = records.filter(r => r.status === '已完成' && r.score != null)
  const avg = scored.length
    ? Math.round(scored.reduce((s, r) => s + (r.score ?? 0), 0) / scored.length)
    : 0
  const pass = scored.length
    ? Math.round(scored.filter(r => (r.score ?? 0) >= 60).length / scored.length * 100)
    : 0
  return {
    total: records.length,
    avg,
    pass,
    ongoing: records.filter(r => r.status === '进行中').length
  }
})

const statCards = computed(() => [
  { key: 'total',   label: '我的面试', value: stats.value.total,   color: '#409EFF' },
  { key: 'avg',     label: '平均分',   value: stats.value.avg,     color: '#F56C6C' },
  { key: 'pass',    label: '通过率',   value: `${stats.value.pass}%`, color: '#67C23A' },
  { key: 'ongoing', label: '进行中',   value: stats.value.ongoing, color: '#E6A23C' }
])

// 最近 5 条自己的面试记录
const recent = computed(() =>
  [...recordStore.allRecords]
    .sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)))
    .slice(0, 5)
)

const ongoingRecord = computed(() => recordStore.allRecords.find(r => r.status === '进行中'))

function statusTag(status: string) {
  const map: Record<string, string> = {
    '已完成': 'success', '进行中': 'primary', '待开始': 'warning', '已取消': 'info'
  }
  return map[status] ?? 'info'
}

function formatRelativeTime(iso: string) {
  if (!iso) return '-'
  const diff = Date.now() - new Date(iso).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins}分钟前`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days}天前`
  return new Date(iso).toLocaleDateString('zh-CN')
}

function goToRecord(r: any) {
  if (r.status === '已完成') router.push(`/records/${r.id}/report`)
  else router.push(`/records/${r.id}/interview`)
}

onMounted(async () => {
  loading.value = true
  try {
    await recordStore.fetchRecords()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped lang="scss">
.candidate-home {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 960px;
  margin: 0 auto;
}

.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, #1a2038 0%, #283593 100%);
  border-radius: 12px;
  padding: 24px 28px;
  color: #fff;

  .hero-left {
    display: flex;
    align-items: center;
    gap: 16px;

    .hero-avatar {
      width: 52px;
      height: 52px;
      border-radius: 50%;
      background: rgba(255,255,255,0.2);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 22px;
      font-weight: 700;
      border: 2px solid rgba(255,255,255,0.3);
    }
    .hero-title { font-size: 20px; font-weight: 700; }
    .hero-sub { font-size: 13px; color: rgba(255,255,255,0.7); margin-top: 4px; }
  }
}

.continue-alert { border-radius: 8px; }

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;

  @media (max-width: 900px) { grid-template-columns: repeat(2, 1fr); }

  .stat-card {
    background: #fff;
    border-radius: 12px;
    padding: 18px 20px;
    text-align: center;
    border: 1px solid rgba(0,0,0,0.04);
    box-shadow: 0 2px 8px rgba(0,0,0,0.04);
    .stat-value { font-size: 30px; font-weight: 700; }
    .stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
  }
}

.recent-card {
  :deep(.el-card__body) { padding: 0; }
  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    .card-header-title {
      display: flex;
      align-items: center;
      gap: 6px;
      font-weight: 600;
      font-size: 15px;
      color: #303133;
    }
  }
}

.recent-list { padding: 4px 0; }

.recent-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  cursor: pointer;
  transition: background 0.15s;
  border-bottom: 1px solid #f5f5f5;

  &:last-child { border-bottom: none; }
  &:hover { background: #f9fafc; }

  .recent-info {
    flex: 1;
    min-width: 0;
    .recent-name { font-size: 14px; font-weight: 600; color: #303133; }
    .recent-time { font-size: 12px; color: #909399; margin-top: 2px; }
  }
  .recent-score {
    font-size: 13px;
    font-weight: 700;
    color: #409EFF;
  }
}
</style>
