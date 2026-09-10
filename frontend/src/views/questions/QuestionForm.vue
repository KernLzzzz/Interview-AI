<template>
  <div class="page-container">
    <div class="page-header">
      <div class="breadcrumb">
        <el-button text @click="$router.push('/questions')">
          <el-icon><ArrowLeft /></el-icon> 返回题库
        </el-button>
        <span class="sep">/</span>
        <span>{{ isEdit ? '编辑题目' : '新建题目' }}</span>
      </div>
    </div>

    <el-card class="form-card">
      <template #header>
        <span>{{ isEdit ? '编辑题目' : '新建题目' }}</span>
      </template>

      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-width="110px"
        style="max-width: 680px"
      >
        <el-form-item label="题目内容" prop="content">
          <el-input
            v-model="formData.content"
            type="textarea"
            :rows="4"
            placeholder="请输入题目内容"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="题型" prop="type">
          <el-radio-group v-model="formData.type">
            <el-radio-button v-for="t in typeOpts" :key="t" :label="t">{{ t }}</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="难度" prop="difficulty">
          <el-rate
            v-model="formData.difficulty"
            :max="3"
            :texts="difficultyTexts"
            show-text
            :colors="['#99A9BF', '#F7BA2A', '#FF9900']"
          />
        </el-form-item>

        <el-form-item label="所属场景" prop="scenarioId">
          <el-select v-model="formData.scenarioId" placeholder="请选择面试场景" style="width: 240px">
            <el-option
              v-for="s in questionStore.scenarios"
              :key="s.id"
              :label="s.name"
              :value="s.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="参考答案" prop="expectedAnswer">
          <el-input
            v-model="formData.expectedAnswer"
            type="textarea"
            :rows="5"
            maxlength="5000"
            show-word-limit
            placeholder="仅管理员和评测服务可见，不会下发给候选人"
          />
        </el-form-item>

        <el-form-item label="评分关键词" prop="keywords">
          <el-input
            v-model="formData.keywords"
            maxlength="500"
            placeholder="使用逗号分隔，如：事务、隔离级别、MVCC"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">
            {{ isEdit ? '保存修改' : '创建题目' }}
          </el-button>
          <el-button @click="$router.push('/questions')">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useQuestionStore } from '@/stores/question'
import type { Question } from '@/types'
import { getAdminQuestionApi, voToQuestion } from '@/api/question'

const route = useRoute()
const router = useRouter()
const questionStore = useQuestionStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)

const isEdit = computed(() => !!route.params.id)

const formData = ref<Omit<Question, 'createTime' | 'status'> & { createTime?: string }>({
  id: 0,
  scenarioId: 0,
  content: '',
  type: '技术',
  difficulty: 2,
  expectedAnswer: '',
  keywords: ''
})

const rules = {
  content: [
    { required: true, message: '请输入题目内容', trigger: 'blur' },
    { min: 5, max: 500, message: '题目内容应在5-500字符之间', trigger: 'blur' }
  ],
  type: [{ required: true, message: '请选择题型', trigger: 'change' }],
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
  scenarioId: [{ required: true, message: '请选择所属场景', trigger: 'change' }]
}

const typeOpts = ['技术', '行为', 'HR面']
const difficultyTexts = ['简单', '中等', '困难']

onMounted(async () => {
  // 拉取题目 + 场景（场景用于下拉，题目用于编辑态回填）
  await questionStore.fetchQuestions()
  if (isEdit.value) {
    const id = Number(route.params.id)
    const question = voToQuestion(await getAdminQuestionApi(id))
    if (question) {
      Object.assign(formData.value, question)
    } else {
      ElMessage.error('题目不存在')
      router.push('/questions')
    }
  } else if (questionStore.scenarios.length > 0) {
    formData.value.scenarioId = questionStore.scenarios[0].id
  }
})

async function handleSubmit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    const question: Question = {
      id: isEdit.value ? formData.value.id : 0,
      scenarioId: formData.value.scenarioId,
      content: formData.value.content,
      type: formData.value.type,
      difficulty: formData.value.difficulty,
      status: '启用',
      createTime: formData.value.createTime || new Date().toISOString()
    }
    await questionStore.saveQuestion(question)
    ElMessage.success(isEdit.value ? '题目已更新' : '题目创建成功')
    router.push('/questions')
  } finally {
    submitting.value = false
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
  .breadcrumb {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 14px;
    color: #606266;
    .sep { color: #C0C4CC; }
  }
}

.form-card {
  :deep(.el-rate) {
    height: 32px;
    line-height: 32px;
  }
}
</style>
