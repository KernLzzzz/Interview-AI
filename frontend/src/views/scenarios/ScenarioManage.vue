<template>
  <div class="page-container">
    <div class="page-header">
      <h2>场景管理</h2>
      <el-button v-if="isAdmin" type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon> 新建场景
      </el-button>
    </div>

    <el-card class="table-card">
      <el-table
        v-loading="loading"
        :data="scenarios"
        border
        stripe
        row-key="id"
      >
        <el-table-column prop="id" label="ID" width="60" align="center" />
        <el-table-column prop="name" label="场景名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="techField" label="技术领域" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.techField || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="难度" width="110" align="center">
          <template #default="{ row }">
            {{ difficultyLabel(row.difficulty) }}
          </template>
        </el-table-column>
        <el-table-column prop="questionCount" label="题量" width="70" align="center" />
        <el-table-column prop="sortOrder" label="排序" width="70" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <template v-if="isAdmin">
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </template>
            <span v-else style="color:#c0c4cc;font-size:12px">只读</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑场景' : '新建场景'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
        <el-form-item label="场景名称" prop="name">
          <el-input v-model="formData.name" placeholder="如：Java后端工程师" maxlength="100" />
        </el-form-item>
        <el-form-item label="技术领域" prop="techField">
          <el-select v-model="formData.techField" placeholder="选择技术领域" style="width:100%">
            <el-option v-for="f in techFields" :key="f" :label="f" :value="f" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度" prop="difficulty">
          <el-select v-model="formData.difficulty" style="width:100%">
            <el-option label="初级" :value="1" />
            <el-option label="中级" :value="2" />
            <el-option label="高级" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序号" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :value="1">上架</el-radio>
            <el-radio :value="0">下架</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  listScenariosApi,
  createScenarioApi,
  updateScenarioApi,
  deleteScenarioApi,
  type Scenario
} from '@/api/scenario'

const authStore = useAuthStore()
const isAdmin = computed(() => authStore.userRole === '管理员')

const scenarios = ref<Scenario[]>([])
const loading = ref(false)

const techFields = ['前端', '后端', '算法', '大数据', '测试', 'AI']

function difficultyLabel(d?: number) {
  return d === 1 ? '初级' : d === 2 ? '中级' : d === 3 ? '高级' : '-'
}

async function fetchScenarios() {
  loading.value = true
  try {
    scenarios.value = await listScenariosApi()
  } finally {
    loading.value = false
  }
}

// ===================== 新建/编辑 =====================
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const submitting = ref(false)
const editingId = ref<number | null>(null)

const formData = reactive({
  name: '',
  techField: '',
  difficulty: 2,
  sortOrder: 0,
  status: 1,
  description: ''
})

const rules = {
  name: [{ required: true, message: '请输入场景名称', trigger: 'blur' }],
  techField: [{ required: true, message: '请选择技术领域', trigger: 'change' }]
}

function openCreate() {
  isEdit.value = false
  editingId.value = null
  Object.assign(formData, { name: '', techField: '', difficulty: 2, sortOrder: 0, status: 1, description: '' })
  dialogVisible.value = true
}

function openEdit(row: Scenario) {
  isEdit.value = true
  editingId.value = row.id
  Object.assign(formData, {
    name: row.name,
    techField: row.techField ?? '',
    difficulty: row.difficulty ?? 2,
    sortOrder: row.sortOrder ?? 0,
    status: row.status ?? 1,
    description: row.description ?? ''
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (isEdit.value && editingId.value != null) {
      await updateScenarioApi(editingId.value, { ...formData })
      ElMessage.success('场景已更新')
    } else {
      await createScenarioApi({ ...formData })
      ElMessage.success('场景已创建')
    }
    dialogVisible.value = false
    await fetchScenarios()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: Scenario) {
  const confirmed = await ElMessageBox.confirm(
    `确认删除场景「${row.name}」？其下题目可能受影响。`,
    '删除确认',
    { type: 'warning' }
  ).catch(() => false)
  if (!confirmed) return
  await deleteScenarioApi(row.id)
  ElMessage.success('场景已删除')
  await fetchScenarios()
}

onMounted(fetchScenarios)
</script>

<style scoped lang="scss">
.page-container {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  h2 { margin: 0; color: #303133; }
}

.table-card :deep(.el-card__body) { padding: 0; }
</style>
