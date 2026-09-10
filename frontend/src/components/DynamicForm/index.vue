<template>
  <el-form
    ref="formRef"
    :model="formModel"
    :rules="computedRules"
    label-width="120px"
    v-bind="$attrs"
  >
    <template v-for="field in visibleFields" :key="field.key">
      <el-form-item :label="field.label" :prop="field.key">
        <!-- Input -->
        <el-input
          v-if="field.type === 'input'"
          v-model="formModel[field.key]"
          v-bind="field.props"
          @change="handleChange(field.key, $event)"
        />

        <!-- Textarea -->
        <el-input
          v-else-if="field.type === 'textarea'"
          v-model="formModel[field.key]"
          type="textarea"
          :rows="4"
          :maxlength="(field.props?.maxlength as number) || 500"
          show-word-limit
          v-bind="field.props"
          @change="handleChange(field.key, $event)"
        />

        <!-- Select -->
        <el-select
          v-else-if="field.type === 'select'"
          v-model="formModel[field.key]"
          class="w-full"
          v-bind="field.props"
          @change="handleChange(field.key, $event)"
        >
          <el-option
            v-for="opt in field.options"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>

        <!-- InputNumber -->
        <el-input-number
          v-else-if="field.type === 'number'"
          v-model="formModel[field.key]"
          class="w-full"
          v-bind="field.props"
          @change="handleChange(field.key, $event)"
        />

        <!-- Switch -->
        <el-switch
          v-else-if="field.type === 'switch'"
          v-model="formModel[field.key]"
          v-bind="field.props"
          @change="handleChange(field.key, $event)"
        />

        <!-- DateTimePicker -->
        <el-date-picker
          v-else-if="field.type === 'datetime'"
          v-model="formModel[field.key]"
          type="datetime"
          class="w-full"
          v-bind="field.props"
          @change="handleChange(field.key, $event)"
        />
      </el-form-item>
    </template>
  </el-form>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { FormInstance } from 'element-plus'
import type { FormField, LinkageRule } from '@/types'

interface Props {
  fields: FormField[]
  linkageRules?: LinkageRule[]
  modelValue?: Record<string, any>
}

const props = withDefaults(defineProps<Props>(), {
  linkageRules: () => [],
  modelValue: () => ({})
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, any>): void
  (e: 'change', key: string, value: any): void
  (e: 'fieldVisibilityChange', key: string, visible: boolean): void
}>()

const formRef = ref<FormInstance>()

// 内部表单数据
const formModel = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

// 字段可见性状态
const fieldVisibility = ref<Record<string, boolean>>({})

// 初始化可见性
props.fields.forEach(f => {
  fieldVisibility.value[f.key] = f.visible !== false
})

// 可见字段
const visibleFields = computed(() => {
  return props.fields.filter(f => fieldVisibility.value[f.key] !== false)
})

// 计算校验规则（隐藏字段不校验）
const computedRules = computed(() => {
  const rules: Record<string, any[]> = {}
  props.fields.forEach(f => {
    if (fieldVisibility.value[f.key] !== false && f.rules) {
      rules[f.key] = f.rules
    }
  })
  return rules
})

// 处理联动逻辑
function applyLinkage(key: string, value: any) {
  if (!props.linkageRules) return
  props.linkageRules.forEach(rule => {
    if (rule.trigger === key) {
      const result = rule.condition(value)
      const shouldShow = rule.action === 'show' ? result : !result
      if (fieldVisibility.value[rule.target] !== shouldShow) {
        fieldVisibility.value[rule.target] = shouldShow
        emit('fieldVisibilityChange', rule.target, shouldShow)
        // 隐藏时清空目标字段值
        if (!shouldShow && formModel.value[rule.target] !== undefined) {
          const newModel = { ...formModel.value }
          newModel[rule.target] = undefined
          emit('update:modelValue', newModel)
        }
      }
    }
  })
}

function handleChange(key: string, value: any) {
  applyLinkage(key, value)
  emit('change', key, value)
}

// 监听联动触发字段的变化
watch(
  () => formModel.value,
  (newVal) => {
    if (!props.linkageRules) return
    const triggerKeys = new Set(props.linkageRules.map(r => r.trigger))
    triggerKeys.forEach(key => {
      applyLinkage(key, newVal[key])
    })
  },
  { deep: true, immediate: true }
)

// 暴露方法
defineExpose({
  validate: () => formRef.value?.validate(),
  resetFields: () => formRef.value?.resetFields(),
  clearValidate: () => formRef.value?.clearValidate(),
  getFieldVisibility: () => ({ ...fieldVisibility.value })
})
</script>

<style scoped lang="scss">
.w-full {
  width: 100%;
}
</style>
