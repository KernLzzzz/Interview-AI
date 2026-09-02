<template>
  <div class="auth-page">
    <div class="auth-card">
      <!-- Logo区域 -->
      <div class="auth-logo">
        <el-icon size="36" color="#409EFF"><Monitor /></el-icon>
        <h1>面试平台管理系统</h1>
        <p>创建新账号</p>
      </div>

      <!-- 注册表单 -->
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="auth-form"
        @keyup.enter="handleRegister"
      >
        <!-- 用户名 + 手机号 同行 -->
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="form.username"
                placeholder="2-16个字符"
                prefix-icon="User"
                clearable
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号（选填）" prop="phone">
              <el-input
                v-model="form.phone"
                placeholder="11位手机号"
                prefix-icon="Phone"
                clearable
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 邮箱 -->
        <el-form-item label="邮箱" prop="email">
          <el-input
            v-model="form.email"
            placeholder="用于登录的邮箱地址"
            prefix-icon="Message"
            clearable
          />
        </el-form-item>

        <!-- 密码 + 确认密码 同行 -->
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="form.password"
                type="password"
                placeholder="6-30位，含字母和数字"
                prefix-icon="Lock"
                show-password
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input
                v-model="form.confirmPassword"
                type="password"
                placeholder="再次输入密码"
                prefix-icon="Lock"
                show-password
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 提交按钮 -->
        <el-form-item>
          <el-button
            type="primary"
            :loading="authStore.loading"
            class="submit-btn"
            @click="handleRegister"
          >
            {{ authStore.loading ? '注册中...' : '立即注册' }}
          </el-button>
        </el-form-item>

        <div class="auth-footer">
          已有账号？
          <el-link type="primary" @click="router.push('/login')">立即登录</el-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { validators } from '@/utils/validators'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()

const form = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  phone: ''
})

// 确认密码自定义校验
function validateConfirm(rule: any, value: string, callback: Function) {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

// 密码规则对齐后端：6-30位，含字母和数字
function validatePassword(rule: any, value: string, callback: Function) {
  const reg = /^(?=.*[a-zA-Z])(?=.*\d).{6,30}$/
  if (!reg.test(value)) {
    callback(new Error('密码需6-30位，且包含字母和数字'))
  } else {
    callback()
  }
}

// 用户名校验
function validateUsername(rule: any, value: string, callback: Function) {
  if (!value) return callback(new Error('请输入用户名'))
  if (value.length < 2 || value.length > 16) return callback(new Error('用户名为2-16个字符'))
  callback()
}

const rules = {
  username: [{ required: true, validator: validateUsername, trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { validator: validators.email, trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { validator: validatePassword, trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ],
  phone: [
    { validator: validators.phone, trigger: 'blur' }
  ]
}

async function handleRegister() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const result = await authStore.register(form)
  if (result.success) {
    ElMessage.success('注册成功，正在登录...')
    // 注册即自动登录（后端固定 candidate 角色）
    const loginRes = await authStore.login({
      username: form.username,
      password: form.password,
      remember: true
    })
    router.push(loginRes.success ? '/' : '/login')
  } else {
    ElMessage.error(result.message)
  }
}
</script>

<style lang="scss" scoped>
.auth-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #1a2038 0%, #2d3561 50%, #1a2038 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.auth-card {
  background: #fff;
  border-radius: 16px;
  padding: 40px 40px 32px;
  width: 100%;
  max-width: 560px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.auth-logo {
  text-align: center;
  margin-bottom: 32px;
  h1 {
    font-size: 20px;
    font-weight: 700;
    color: #303133;
    margin: 12px 0 6px;
  }
  p {
    font-size: 13px;
    color: #909399;
  }
}

.auth-form {
  :deep(.el-form-item__label) {
    font-weight: 500;
    color: #606266;
    padding-bottom: 4px;
  }
  :deep(.el-input__wrapper) {
    border-radius: 8px;
  }
}

.submit-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  border-radius: 8px;
  margin-top: 4px;
}

.auth-footer {
  text-align: center;
  font-size: 13px;
  color: #909399;
  margin-top: 12px;
}
</style>
