<template>
  <div class="candidate-home">
    <div class="hero">
      <div class="hero-copy">
        <span class="hero-eyebrow">CANDIDATE WORKSPACE · {{ sessionState }}</span>
        <h1>{{ authStore.username }}，<br><em>{{ heroHeadline }}</em></h1>
        <p>{{ heroDescription }}</p>
        <div class="hero-actions">
          <el-button type="primary" size="large" @click="primaryAction">
            <el-icon><VideoPlay /></el-icon>{{ primaryActionLabel }}
          </el-button>
          <el-button size="large" @click="$router.push('/records')">查看成长记录</el-button>
        </div>
      </div>

      <div class="readiness-panel">
        <div class="readiness-head"><span>本轮准备度</span><small>READINESS</small></div>
        <div class="readiness-score">
          <strong>{{ stats.avg || '--' }}</strong><span v-if="stats.avg">/100</span>
        </div>
        <div class="readiness-track"><i :style="{ width: `${stats.avg}%` }"></i></div>
        <p>{{ readinessCopy }}</p>
        <div class="readiness-meta">
          <span><i></i>{{ stats.total }} 次练习</span>
          <span>{{ stats.ongoing ? `${stats.ongoing} 场进行中` : '当前无进行中面试' }}</span>
        </div>
      </div>
    </div>

    <div class="stat-cards" v-loading="loading">
      <div v-for="card in statCards" :key="card.key" class="stat-card" :class="`tone-${card.tone}`">
        <span class="stat-index">{{ card.index }}</span>
        <div>
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value">{{ card.value }}</div>
        </div>
        <small>{{ card.note }}</small>
      </div>
    </div>

    <section class="recent-card">
      <div class="card-header">
        <div>
          <span>SESSION LOG</span>
          <h2>最近面试</h2>
        </div>
        <el-button link type="primary" @click="$router.push('/records')">查看全部记录 →</el-button>
      </div>
      <div class="recent-list" v-loading="loading">
        <button v-for="r in recent" :key="r.id" type="button" class="recent-item" @click="goToRecord(r)">
          <span class="record-mark">{{ String(r.id).padStart(2, '0').slice(-2) }}</span>
          <div class="recent-info">
            <div class="recent-name">{{ r.scenarioName }}</div>
            <div class="recent-time">{{ formatRelativeTime(r.createdAt) }} · {{ modeLabel(r.interviewMode) }}</div>
          </div>
          <el-tag :type="statusTag(r.status)" size="small">{{ r.status }}</el-tag>
          <div v-if="r.score != null" class="recent-score"><strong>{{ r.score }}</strong><span>分</span></div>
          <el-icon v-else color="#94a5af"><ArrowRight /></el-icon>
        </button>

        <el-empty v-if="!loading && recent.length === 0" description="还没有面试记录，先建立你的第一条评测基线" :image-size="70">
          <el-button type="primary" @click="$router.push('/scenarios')">创建第一场面试</el-button>
        </el-empty>
      </div>
    </section>
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

const stats = computed(() => {
  const records = recordStore.allRecords
  const scored = records.filter(r => r.status === '已完成' && r.score != null)
  const avg = scored.length
    ? Math.round(scored.reduce((sum, record) => sum + (record.score ?? 0), 0) / scored.length)
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
  { key: 'total', index: '01', label: '累计训练', value: stats.value.total, note: '完整面试场次', tone: 'signal' },
  { key: 'avg', index: '02', label: '平均得分', value: stats.value.avg || '--', note: '已完成场次', tone: 'ink' },
  { key: 'pass', index: '03', label: '达标率', value: `${stats.value.pass}%`, note: '分数不低于 60', tone: 'good' },
  { key: 'ongoing', index: '04', label: '待完成', value: stats.value.ongoing, note: '可继续作答', tone: 'warm' }
])

const recent = computed(() =>
  [...recordStore.allRecords]
    .sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)))
    .slice(0, 5)
)

const ongoingRecord = computed(() => recordStore.allRecords.find(r => r.status === '进行中'))
const sessionState = computed(() => ongoingRecord.value ? 'SESSION IN PROGRESS' : 'READY FOR NEXT SESSION')
const heroHeadline = computed(() => ongoingRecord.value ? '把这场面试完整答完。' : '今天练一次真实表达。')
const heroDescription = computed(() => ongoingRecord.value
  ? `“${ongoingRecord.value.scenarioName}”仍在进行中，从上次离开的地方继续。`
  : '带上目标岗位与 JD，系统会为你组合更贴近真实招聘要求的问题。')
const primaryActionLabel = computed(() => ongoingRecord.value ? '继续当前面试' : '创建一场面试')
const readinessCopy = computed(() => {
  if (!stats.value.total) return '完成第一场面试后，这里会显示你的真实评测基线。'
  if (stats.value.avg >= 80) return '表现稳定，下一轮可以提高难度并强化岗位针对性。'
  if (stats.value.avg >= 60) return '基础表达已达标，建议根据逐题证据集中修正薄弱项。'
  return '当前更适合拆分练习：先补全回答结构，再关注表达速度。'
})

function primaryAction() {
  if (ongoingRecord.value) goToRecord(ongoingRecord.value)
  else router.push('/scenarios')
}

function statusTag(status: string) {
  const map: Record<string, string> = {
    '已完成': 'success', '进行中': 'primary', '待开始': 'warning', '已取消': 'info'
  }
  return map[status] ?? 'info'
}

function modeLabel(mode?: string) {
  if (mode === 'video') return '视频'
  if (mode === 'voice') return '语音'
  return '文本'
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

function goToRecord(record: { id: number; status: string }) {
  if (record.status === '已完成') router.push(`/records/${record.id}/report`)
  else router.push(`/records/${record.id}/interview`)
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
  padding: 28px clamp(20px, 3vw, 44px) 52px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  max-width: 1240px;
  margin: 0 auto;
}

.hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(300px, .55fr);
  gap: clamp(28px, 5vw, 72px);
  overflow: hidden;
  min-height: 340px;
  padding: clamp(30px, 4vw, 52px);
  color: #eef7f7;
  background:
    radial-gradient(circle at 78% 5%, rgba(80, 184, 194, .2), transparent 27%),
    linear-gradient(142deg, #0e2233 0%, #123548 100%);
  border-radius: 7px;
}

.hero::after {
  position: absolute;
  right: -100px;
  bottom: -250px;
  width: 500px;
  height: 500px;
  border: 1px solid rgba(171, 219, 222, .15);
  border-radius: 50%;
  box-shadow: 0 0 0 54px rgba(171, 219, 222, .03), 0 0 0 108px rgba(171, 219, 222, .02);
  content: '';
}

.hero-copy {
  position: relative;
  z-index: 1;
  align-self: center;
  h1 { margin: 14px 0 17px; font: 600 clamp(32px, 4vw, 50px)/1.18 "Bahnschrift", "Noto Sans SC", sans-serif; letter-spacing: -.035em; }
  h1 em { color: #9bd6da; font-style: normal; }
  > p { max-width: 620px; color: #a9c0c9; font-size: 14px; line-height: 1.8; }
}

.hero-eyebrow { color: #57bdc4; font: 700 10px/1 ui-monospace, monospace; letter-spacing: .18em; }

.hero-actions {
  display: flex;
  gap: 10px;
  margin-top: 28px;
  .el-button { height: 44px; border-radius: 4px; }
  .el-button:not(.el-button--primary) { color: #d5e6e8; background: transparent; border-color: rgba(213, 230, 232, .35); }
  .el-icon { margin-right: 7px; }
}

.readiness-panel {
  position: relative;
  z-index: 1;
  align-self: center;
  padding: 22px 0 5px 28px;
  border-left: 1px solid rgba(197, 226, 229, .2);
}

.readiness-head { display: flex; justify-content: space-between; align-items: center; color: #d9eaec; font-size: 12px; }
.readiness-head small { color: #6f99a6; font: 700 9px/1 ui-monospace, monospace; letter-spacing: .14em; }
.readiness-score { display: flex; align-items: baseline; gap: 5px; margin: 19px 0 11px; }
.readiness-score strong { font: 600 58px/.9 "Bahnschrift", sans-serif; letter-spacing: -.05em; }
.readiness-score span { color: #6f99a6; font: 12px/1 ui-monospace, monospace; }
.readiness-track { height: 3px; overflow: hidden; background: rgba(255,255,255,.12); }
.readiness-track i { display: block; height: 100%; background: #62c0c5; transition: width .6s ease; }
.readiness-panel > p { min-height: 42px; margin: 16px 0 20px; color: #9eb6c0; font-size: 11px; line-height: 1.7; }
.readiness-meta { display: flex; justify-content: space-between; gap: 12px; color: #7597a4; font-size: 10px; }
.readiness-meta span:first-child { color: #9bcbbf; }
.readiness-meta i { display: inline-block; width: 6px; height: 6px; margin-right: 6px; border-radius: 50%; background: #55ad94; }

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 6px;

  .stat-card {
    position: relative;
    display: grid;
    grid-template-columns: 32px 1fr;
    gap: 8px;
    min-height: 128px;
    padding: 22px;
    border-right: 1px solid var(--line);
    &:last-child { border-right: 0; }
    .stat-index { color: #9badb7; font: 600 10px/1 ui-monospace, monospace; }
    .stat-label { color: #617786; font-size: 11px; }
    .stat-value { margin-top: 7px; color: #102238; font: 650 31px/1 "Bahnschrift", sans-serif; }
    small { grid-column: 2; align-self: end; color: #9aabb4; font-size: 10px; }
    &.tone-signal .stat-value { color: #167487; }
    &.tone-good .stat-value { color: #21806b; }
    &.tone-warm .stat-value { color: #a96e25; }
  }
}

.recent-card {
  margin-top: 4px;
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 6px;
}

.card-header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  padding: 22px 24px 17px;
  border-bottom: 1px solid var(--line);
  span { color: var(--signal); font: 700 9px/1 ui-monospace, monospace; letter-spacing: .17em; }
  h2 { margin-top: 8px; color: var(--ink-950); font-size: 18px; }
}

.recent-list { min-height: 80px; }

.recent-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 15px 24px;
  color: inherit;
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1px solid #edf1f3;
  cursor: pointer;
  transition: background .15s, padding-left .15s;
  &:last-child { border-bottom: none; }
  &:hover { padding-left: 28px; background: #f5f9f9; }
  .record-mark { color: #9aadb6; font: 600 11px/1 ui-monospace, monospace; }
  .recent-info {
    flex: 1;
    min-width: 0;
    .recent-name { color: #213c4e; font-size: 13px; font-weight: 650; }
    .recent-time { margin-top: 4px; color: #8a9ca6; font-size: 10px; }
  }
  .recent-score {
    min-width: 52px;
    color: #167487;
    text-align: right;
    strong { font: 650 20px/1 "Bahnschrift", sans-serif; }
    span { margin-left: 3px; font-size: 10px; }
  }
}

@media (max-width: 900px) {
  .hero { grid-template-columns: 1fr; }
  .readiness-panel { padding: 22px 0 0; border-top: 1px solid rgba(197, 226, 229, .2); border-left: 0; }
  .stat-cards { grid-template-columns: repeat(2, 1fr); }
  .stat-cards .stat-card:nth-child(2) { border-right: 0; }
  .stat-cards .stat-card:nth-child(-n+2) { border-bottom: 1px solid var(--line); }
}

@media (max-width: 560px) {
  .candidate-home { padding: 16px 12px 36px; }
  .hero { min-height: auto; padding: 28px 22px; }
  .hero-actions { align-items: stretch; flex-direction: column; }
  .stat-cards { grid-template-columns: 1fr; }
  .stat-cards .stat-card { min-height: 104px; border-right: 0; border-bottom: 1px solid var(--line); }
  .stat-cards .stat-card:last-child { border-bottom: 0; }
  .card-header { align-items: flex-start; gap: 12px; }
  .recent-item { padding-inline: 16px; }
  .record-mark { display: none; }
}
</style>
