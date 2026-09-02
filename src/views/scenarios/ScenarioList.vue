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
          @click="startInterview(s)"
        >
          开始面试
        </el-button>
      </el-card>
    </div>

    <el-empty v-if="!loading && scenarios.length === 0" description="暂无可用场景" />
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

onMounted(async () => {
  loading.value = true
  try {
    scenarios.value = await listScenariosApi()
  } finally {
    loading.value = false
  }
})

async function startInterview(s: Scenario) {
  startingId.value = s.id
  try {
    const record = await recordStore.createRecord(s.id)
    ElMessage.success(`已创建「${s.name}」面试，开始作答`)
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
</style>
