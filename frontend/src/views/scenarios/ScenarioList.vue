<template>
  <div class="page-container">
    <div class="page-header">
      <h2>场景广场</h2>
      <p class="sub">选择一个面试场景，开始你的 AI 模拟面试</p>
    </div>

    <div v-loading="loading" class="scenario-grid">
      <el-card
        v-for="s in scenarios"
        :key="s.id"
        class="scenario-card"
        shadow="hover"
      >
        <template #header>
          <div class="card-header">
            <span class="name">{{ s.name }}</span>
            <el-tag size="small" effect="plain">{{ s.techField || '通用' }}</el-tag>
          </div>
        </template>

        <p class="desc">{{ s.description || '暂无描述' }}</p>

        <div class="meta">
          <span class="meta-item">难度</span>
          <el-rate :model-value="s.difficulty ?? 1" :max="3" disabled />
        </div>
        <div class="meta">
          <span class="meta-item">题量</span>
          <span>{{ s.questionCount ?? '-' }} 道</span>
        </div>

        <el-button
          type="primary"
          class="start-btn"
          :loading="startingId === s.id"
          @click="chooseMode(s)"
        >
          开始面试
        </el-button>
      </el-card>
    </div>

    <el-empty v-if="!loading && scenarios.length === 0" description="暂无可用场景" />

    <el-dialog v-model="modeDialog" width="720px" class="mode-dialog" :show-close="false">
      <template #header>
        <div class="mode-heading">
          <span>CHOOSE YOUR ROOM</span>
          <h3>选择面试方式</h3>
          <p>{{ selectedScenario?.name }} · 进入前将进行设备与环境检测</p>
        </div>
      </template>
      <div class="mode-grid">
        <button
          v-for="item in modeOptions"
          :key="item.value"
          class="mode-card"
          :class="{ selected: selectedMode === item.value }"
          type="button"
          @click="selectedMode = item.value"
        >
          <span class="mode-index">{{ item.index }}</span>
          <el-icon><component :is="item.icon" /></el-icon>
          <strong>{{ item.label }}</strong>
          <small>{{ item.description }}</small>
          <span class="permission">{{ item.permission }}</span>
        </button>
      </div>
      <template #footer>
        <div class="dialog-actions">
          <p><i></i> 录音录像仅用于本场模拟面试和评测</p>
          <div>
            <el-button @click="modeDialog = false">稍后再说</el-button>
            <el-button type="primary" :loading="startingId !== null" @click="startInterview">进入候场厅</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listScenariosApi, type Scenario } from '@/api/scenario'
import { useRecordStore } from '@/stores/record'

const router = useRouter()
const recordStore = useRecordStore()
const scenarios = ref<Scenario[]>([])
const loading = ref(false)
const startingId = ref<number | null>(null)
const modeDialog = ref(false)
const selectedScenario = ref<Scenario | null>(null)
const selectedMode = ref<'text' | 'voice' | 'video'>('video')
const modeOptions = [
  { index: '01', value: 'video' as const, label: '视频面试', icon: 'VideoCamera', description: '模拟远程面试现场，保留完整音视频记录', permission: '摄像头 + 麦克风' },
  { index: '02', value: 'voice' as const, label: '语音面试', icon: 'Microphone', description: '专注表达与逻辑，弱化镜头带来的紧张感', permission: '麦克风' },
  { index: '03', value: 'text' as const, label: '文本面试', icon: 'EditPen', description: '通过文字组织答案，适合安静复盘与练习', permission: '无需设备权限' }
]

onMounted(async () => {
  loading.value = true
  try {
    scenarios.value = await listScenariosApi()
  } finally {
    loading.value = false
  }
})

function chooseMode(s: Scenario) {
  selectedScenario.value = s
  selectedMode.value = 'video'
  modeDialog.value = true
}

async function startInterview() {
  const scenario = selectedScenario.value
  if (!scenario) return
  startingId.value = scenario.id
  try {
    const record = await recordStore.createRecord(scenario.id, selectedMode.value)
    modeDialog.value = false
    ElMessage.success(`面试房间已创建，正在进入候场厅`)
    router.push(`/records/${record.id}/interview`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '创建面试失败')
  } finally {
    startingId.value = null
  }
}
</script>

<style scoped lang="scss">
.page-container {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  h2 { margin: 0 0 4px; color: #303133; }
  .sub { margin: 0; font-size: 13px; color: #909399; }
}

.scenario-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.scenario-card {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    .name { font-weight: 600; font-size: 15px; color: #303133; }
  }
  .desc {
    min-height: 44px;
    margin: 0 0 12px;
    font-size: 13px;
    color: #606266;
    line-height: 1.6;
  }
  .meta {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    color: #606266;
    margin-bottom: 8px;
    .meta-item { color: #909399; width: 28px; }
  }
  .start-btn {
    width: 100%;
    margin-top: 8px;
  }
}

:deep(.mode-dialog) {
  border-radius: 10px;
  overflow: hidden;
  .el-dialog__header { padding: 28px 30px 16px; margin: 0; }
  .el-dialog__body { padding: 8px 30px 20px; }
  .el-dialog__footer { padding: 18px 30px 24px; border-top: 1px solid var(--line); }
}

.mode-heading {
  span { font: 700 10px/1 ui-monospace, monospace; letter-spacing: .2em; color: var(--signal); }
  h3 { margin: 8px 0 5px; font-size: 24px; color: var(--ink-950); }
  p { margin: 0; color: var(--slate-600); font-size: 13px; }
}

.mode-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.mode-card {
  position: relative;
  min-height: 210px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  padding: 20px;
  text-align: left;
  color: var(--ink-950);
  background: #f7f9fa;
  border: 1px solid var(--line);
  border-radius: 7px;
  cursor: pointer;
  transition: border-color .2s, transform .2s, background .2s;
  &:hover { transform: translateY(-2px); border-color: #96b8c1; }
  &.selected { background: #ecf6f7; border: 2px solid var(--signal); padding: 19px; }
  .el-icon { margin-top: 18px; font-size: 28px; color: var(--signal); }
  strong { font-size: 16px; }
  small { color: var(--slate-600); line-height: 1.6; }
  .mode-index { position: absolute; top: 14px; right: 16px; font: 600 11px/1 ui-monospace, monospace; color: #9bacba; }
  .permission { margin-top: auto; font: 600 10px/1 ui-monospace, monospace; color: #496575; letter-spacing: .04em; }
}

.dialog-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  p { display: flex; align-items: center; gap: 8px; margin: 0; color: var(--slate-600); font-size: 12px; }
  i { width: 7px; height: 7px; border-radius: 50%; background: #2a9b7f; }
}

@media (max-width: 760px) {
  :deep(.mode-dialog) { width: calc(100% - 24px) !important; }
  .mode-grid { grid-template-columns: 1fr; }
  .mode-card { min-height: 145px; }
  .dialog-actions { align-items: stretch; flex-direction: column; }
}
</style>
