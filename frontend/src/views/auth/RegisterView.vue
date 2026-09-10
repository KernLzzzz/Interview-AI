<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-logo">
        <span class="auth-mark">IA</span>
        <span class="eyebrow">NEW CANDIDATE</span>
        <h1>创建候选人档案</h1>
        <p>完成注册后，直接建立你的第一条面试评测基线</p>
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
  background:
    radial-gradient(circle at 13% 18%, rgba(75, 171, 181, .2), transparent 21%),
    linear-gradient(108deg, #0b1d2c 0%, #123345 36%, #f4f7f8 36%, #f4f7f8 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.auth-card {
  margin-left: 26%;
  background: rgba(255, 255, 255, .96);
  border: 1px solid #d9e3e7;
  border-radius: 6px;
  padding: 38px 42px 32px;
  width: 100%;
  max-width: 620px;
  box-shadow: 0 24px 70px rgba(8, 30, 45, .16);
}

.auth-logo {
  position: relative;
  margin-bottom: 32px;
  padding-left: 58px;
  .auth-mark {
    position: absolute;
    top: 0;
    left: 0;
    display: grid;
    place-items: center;
    width: 42px;
    height: 42px;
    color: #167487;
    border: 1px solid #8aa3ae;
    border-radius: 50%;
    font: 700 11px/1 ui-monospace, monospace;
  }
  .eyebrow { color: #167487; font: 700 9px/1 ui-monospace, monospace; letter-spacing: .18em; }
  h1 {
    color: #102238;
    font: 650 27px/1.2 "Bahnschrift", "Noto Sans SC", sans-serif;
    margin: 9px 0 7px;
  }
  p {
    font-size: 13px;
    color: #748894;
  }
}

.auth-form {
  :deep(.el-form-item__label) {
    color: #354d5d;
    font-size: 12px;
    font-weight: 650;
    padding-bottom: 7px;
  }
  :deep(.el-input__wrapper) {
    min-height: 43px;
    border-radius: 4px;
    box-shadow: 0 0 0 1px #d5e0e4 inset;
    &:focus-within { box-shadow: 0 0 0 1px #167487 inset, 0 0 0 4px rgba(22, 116, 135, .08); }
  }
}

.submit-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  border-radius: 4px;
  margin-top: 4px;
}

.auth-footer {
  text-align: center;
  font-size: 13px;
  color: #748894;
  margin-top: 12px;
}

@media (max-width: 820px) {
  .auth-page { background: #f4f7f8; padding: 18px; }
  .auth-card { margin-left: 0; padding: 30px 24px 26px; }
}

@media (max-width: 560px) {
  .auth-logo { padding-left: 0; padding-top: 56px; }
  .auth-logo .auth-mark { top: 0; }
  :deep(.el-col) { max-width: 100%; flex: 0 0 100%; }
}
</style>
